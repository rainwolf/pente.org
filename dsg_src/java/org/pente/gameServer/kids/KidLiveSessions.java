package org.pente.gameServer.kids;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import org.apache.log4j.Category;

import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerStorer;

/**
 * Kid state of the players joined to one live server, the chat rule between
 * them, and the per-session buffer of live chat copied to a kid's parent.
 */
public class KidLiveSessions implements KidDataChangeListener {

    private static Category log4j = Category.getInstance(KidLiveSessions.class.getName());

    public static final int BUFFER_TTL_SECONDS = 86400;
    /** Total for all servers; leaves room in Docker's 10 s stop grace for the rest of shutdown. */
    public static final long SHUTDOWN_BUDGET_MILLIS = 5000;
    /** Cap on each SMTP connect/read/write during the shutdown flush. */
    public static final int SHUTDOWN_SEND_TIMEOUT_MILLIS = 1000;

    private final long serverId;
    private final DSGPlayerStorer players;
    private final KidStorer kids;
    private final ChatCopyStore copies;
    private final KidCopyMailer mailer;
    private final LongSupplier clock;

    /** Joined players by name. */
    private final Map<String, LiveKidState> sessions = new ConcurrentHashMap<String, LiveKidState>();
    /** Kid pid -> seq of its latest change. */
    private final Map<Long, Long> changedAt = new ConcurrentHashMap<Long, Long>();
    private final AtomicLong seq = new AtomicLong();
    private volatile boolean shuttingDown;

    public KidLiveSessions(long serverId, DSGPlayerStorer players, KidStorer kids,
                           ChatCopyStore copies, KidCopyMailer mailer) {
        this(serverId, players, kids, copies, mailer, System::currentTimeMillis);
    }

    public KidLiveSessions(long serverId, DSGPlayerStorer players, KidStorer kids,
                           ChatCopyStore copies, KidCopyMailer mailer, LongSupplier clock) {
        this.serverId = serverId;
        this.players = players;
        this.kids = kids;
        this.copies = copies;
        this.mailer = mailer;
        this.clock = clock;
    }

    /** Never throws. Fails closed: a kid whose data cannot be read gets an empty whitelist, copy off. */
    public LiveKidState load(DSGPlayerData d) {
        long loadedSeq = seq.incrementAndGet();
        long pid = d.getPlayerID();
        Long parentPid = d.getParentPid();
        if (parentPid == null) {
            return new LiveKidState(pid, null, null, false, loadedSeq);
        }
        try {
            Set<Long> whitelist = kids.getWhitelist(pid);
            boolean copy = kids.getSettings(pid).isCopyLiveChat();
            return new LiveKidState(pid, parentPid, whitelist, copy, loadedSeq);
        } catch (Throwable t) {
            log4j.error("Kid live state load failed, failing closed: pid=" + pid, t);
            return new LiveKidState(pid, parentPid, null, false, loadedSeq);
        }
    }

    public void join(String name, LiveKidState state) {
        if (state == null) {
            return;
        }
        LiveKidState s = freshIfStale(state);
        sessions.put(name, s);
        // a change that landed after the check above but before the put
        // found no entry to refresh
        if (isStale(s)) {
            LiveKidState fresh = reload(s.getPid());
            if (fresh != null) {
                sessions.replace(name, s, fresh);
            }
        }
    }

    public void leave(String name) {
        LiveKidState s = sessions.remove(name);
        if (s != null && s.isKid()) {
            flush(s);
        }
    }

    /** Null when not joined. */
    public LiveKidState get(String name) {
        return sessions.get(name);
    }

    public boolean allowed(DSGPlayerData a, DSGPlayerData b) {
        return KidChatRules.allowed(party(a), party(b));
    }

    /** Buffers a line the recipient saw, when the recipient is a kid with live-chat copy on. Never throws. */
    public void delivered(DSGPlayerData recipient, String where, String author, String text, Date when) {
        try {
            LiveKidState s = sessions.get(recipient.getName());
            if (s != null && s.isKid() && s.isCopyLiveChat()) {
                copies.append(bufferKey(s.getPid()),
                        KidCopyEmails.liveChatLine(when, where, author, text), BUFFER_TTL_SECONDS);
            }
        } catch (Throwable t) {
            log4j.error("Kid live chat buffer failed", t);
        }
    }

    public void kidDataChanged(long kidPid) {
        changedAt.put(kidPid, seq.incrementAndGet());
        for (Map.Entry<String, LiveKidState> e : sessions.entrySet()) {
            LiveKidState old = e.getValue();
            if (old.getPid() != kidPid) {
                continue;
            }
            LiveKidState fresh = reload(kidPid);
            if (fresh == null || !sessions.replace(e.getKey(), old, fresh)) {
                continue;
            }
            if (old.isKid() && old.isCopyLiveChat() && (!fresh.isKid() || !fresh.isCopyLiveChat())) {
                flush(old);
            }
        }
    }

    String bufferKey(long pid) {
        return "kids:livechat:" + serverId + ":" + pid;
    }

    /**
     * Mails the buffers of joined kids with live-chat copy on, on the calling
     * thread, until the deadline. Buffers not mailed stay for the kid's next
     * leave. Afterwards leave and kidDataChanged no longer flush. Never throws.
     */
    public int flushForShutdown(long deadlineMillis) {
        shuttingDown = true;
        int mailed = 0;
        int left = 0;
        List<LiveKidState> due = new ArrayList<LiveKidState>();
        for (LiveKidState s : sessions.values()) {
            if (s.isKid() && s.isCopyLiveChat()) {
                due.add(s);
            }
        }
        for (int i = 0; i < due.size(); i++) {
            LiveKidState s = due.get(i);
            String key = bufferKey(s.getPid());
            List<String> lines = null;
            try {
                long now = clock.getAsLong();
                if (now >= deadlineMillis) {
                    left += due.size() - i;
                    break;
                }
                lines = copies.drain(key);
                if (lines.isEmpty()) {
                    continue;
                }
                int timeout = (int) Math.max(1, Math.min(deadlineMillis - now, SHUTDOWN_SEND_TIMEOUT_MILLIS));
                if (mailer.copyLiveChatNow(s.getPid(), s.getParentPid(), lines, timeout)) {
                    mailed++;
                } else {
                    copies.restore(key, lines, BUFFER_TTL_SECONDS);
                    left++;
                }
            } catch (Throwable t) {
                log4j.error("Kid live chat shutdown flush failed: pid=" + s.getPid(), t);
                left++;
                if (lines != null && !lines.isEmpty()) {
                    try {
                        copies.restore(key, lines, BUFFER_TTL_SECONDS);
                    } catch (Throwable t2) {
                        log4j.error("Kid live chat buffer restore failed: pid=" + s.getPid(), t2);
                    }
                }
            }
        }
        log4j.info("Kid live-chat shutdown flush: server=" + serverId + " mailed=" + mailed + " left=" + left);
        return mailed;
    }

    private KidChatRules.Party party(DSGPlayerData d) {
        LiveKidState s = sessions.get(d.getName());
        if (s == null || s.getPid() != d.getPlayerID()) {
            return new KidChatRules.Party(d.getPlayerID(), d.isAdmin(), d.isComputer(), null,
                    Collections.<Long>emptySet());
        }
        return new KidChatRules.Party(d.getPlayerID(), d.isAdmin(), d.isComputer(),
                s.getParentPid(), s.getWhitelist());
    }

    private boolean isStale(LiveKidState s) {
        Long changed = changedAt.get(s.getPid());
        return changed != null && changed > s.getLoadedSeq();
    }

    private LiveKidState freshIfStale(LiveKidState s) {
        if (!isStale(s)) {
            return s;
        }
        LiveKidState fresh = reload(s.getPid());
        return fresh == null ? s : fresh;
    }

    /** The player's current state, or null (logged) when the player cannot be loaded. */
    private LiveKidState reload(long pid) {
        try {
            DSGPlayerData d = players.loadPlayer(pid);
            if (d == null) {
                log4j.warn("Kid live state reload: player not found, keeping state: pid=" + pid);
                return null;
            }
            return load(d);
        } catch (Throwable t) {
            log4j.error("Kid live state reload failed, keeping state: pid=" + pid, t);
            return null;
        }
    }

    private void flush(LiveKidState s) {
        if (shuttingDown) {
            return;
        }
        try {
            List<String> lines = copies.drain(bufferKey(s.getPid()));
            if (!lines.isEmpty()) {
                mailer.copyLiveChat(s.getPid(), s.getParentPid(), lines);
            }
        } catch (Throwable t) {
            log4j.error("Kid live chat flush failed: pid=" + s.getPid(), t);
        }
    }
}
