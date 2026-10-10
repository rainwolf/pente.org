package org.pente.gameServer.server;

import org.apache.log4j.Category;
import org.pente.gameServer.event.*;

import java.util.*;

/**
 * All arena join-request state (spec 2026-10-10, R1-R11), shared by every
 * arena table and owned by ArenaServer.
 * <p>
 * Locking: every public method is synchronized, so a change that spans
 * tables (a requester leaving, a claim) is one atomic step. This class never
 * takes the Server.tables lock: Server.removeTable holds that lock while it
 * calls ArenaServerTable.destroy(), which calls tableRemoved() here, so the
 * reverse order would deadlock.
 * <p>
 * Outgoing events go to the Notifier while the lock is held. The production
 * notifier only enqueues on the player's writer, so each client sees events
 * in mutation order. Every event carries copies, because Gson serializes it
 * later on the writer thread.
 */
public class ArenaJoinRequestRegistry {

    private static final Category log4j =
            Category.getInstance(ArenaJoinRequestRegistry.class.getName());

    /** Delivers one event to one player; production wraps DSGEventToPlayerRouter.routeEvent. */
    public interface Notifier {
        void send(String player, DSGEvent event);
    }

    /** What the registry knows about one table: its published state plus its memory. */
    private static final class TableMemory {
        String owner;
        boolean open;
        boolean rated;
        Set<String> players = new HashSet<>();
        /** requester -> seq; seq only grows, so insertion order is seq order */
        final LinkedHashMap<String, Long> pending = new LinkedHashMap<>();
        final Set<String> blocked = new HashSet<>();
        /** requester whose accepted join is queued on this table, or null (R5) */
        String claimed;
    }

    private final Notifier notifier;
    private final Map<Integer, TableMemory> tables = new HashMap<>();
    /** requester -> table whose accept they hold (R5) */
    private final Map<String, Integer> claims = new HashMap<>();
    private long nextSeq = 1;

    public ArenaJoinRequestRegistry(Notifier notifier) {
        this.notifier = notifier;
    }

    // ---- table state, published by each table from its own pump ----------

    public synchronized void publishTable(int table, String owner, Collection<String> players,
                                          boolean noGameInProgress, boolean rated) {
        TableMemory t = tables.get(table);
        if (t == null) {
            t = new TableMemory();
            tables.put(table, t);
        }
        String oldOwner = t.owner;

        t.owner = owner;
        t.rated = rated;
        t.players = new HashSet<>(players);
        t.open = t.players.size() == 1 && noGameInProgress;

        // a player who becomes owner gets the list for their table
        if (owner != null && !owner.equals(oldOwner)) {
            notifySnapshot(table, t, owner);
        }
    }

    // ---- requester actions -------------------------------------------------

    /** R2: create (requester, table) or refuse it; always answers with my-requests (R11). */
    public synchronized void request(int table, String requester, boolean requesterInMainRoom) {
        TableMemory t = tables.get(table);
        String reason = refusal(t, requester, requesterInMainRoom);
        if (reason != null) {
            notifyEnded(requester, table, t, requester, reason);
        } else {
            t.pending.put(requester, nextSeq++);
            notifySnapshot(table, t, t.owner);
        }
        notifyMyRequests(requester);
    }

    /** R3: like a decline the requester gives themselves; always answers (R11). */
    public synchronized void withdraw(int table, String requester) {
        TableMemory t = tables.get(table);
        if (t != null && t.pending.remove(requester) != null) {
            t.blocked.add(requester);
            notifySnapshot(table, t, t.owner);
        }
        notifyMyRequests(requester);
    }

    // ---- owner actions -----------------------------------------------------

    /** R4 and R9; always answers the sender with a snapshot (R11). */
    public synchronized void decline(int table, String sender, String requester) {
        TableMemory t = tables.get(table);
        if (t == null || !sender.equals(t.owner)) {
            log4j.warn("Arena decline at table " + table + " by non-owner " + sender + " ignored");
            notifyEmptySnapshot(table, sender);
            return;
        }
        if (t.pending.remove(requester) != null) {
            t.blocked.add(requester);
            notifyEnded(requester, table, t, requester, DSGArenaRequestEndedEvent.DECLINED);
            notifyMyRequests(requester);
        }
        notifySnapshot(table, t, sender);
    }

    // ---- answers on demand ---------------------------------------------------

    /** Sent when a player joins the main room. */
    public synchronized void sendMyRequests(String requester) {
        notifyMyRequests(requester);
    }

    // ---- read-only observability for tests; never mutates ------------------

    public synchronized List<String> pendingRequesters(int table) {
        TableMemory t = tables.get(table);
        return t == null ? new ArrayList<String>() : new ArrayList<>(t.pending.keySet());
    }

    public synchronized SortedSet<Integer> pendingTables(String requester) {
        return pendingTablesOf(requester);
    }

    public synchronized boolean isBlocked(int table, String requester) {
        TableMemory t = tables.get(table);
        return t != null && t.blocked.contains(requester);
    }

    public synchronized boolean isOpen(int table) {
        TableMemory t = tables.get(table);
        return t != null && t.open;
    }

    // ---- helpers; the caller holds the lock ---------------------------------

    private String refusal(TableMemory t, String requester, boolean requesterInMainRoom) {
        if (t == null || !t.open || t.claimed != null) {
            return DSGArenaRequestEndedEvent.NOT_AVAILABLE;
        }
        if (!requesterInMainRoom || requester.equals(t.owner)
                || isSeatedAnywhere(requester) || claims.containsKey(requester)) {
            return DSGArenaRequestEndedEvent.NOT_AVAILABLE;
        }
        if (requester.startsWith("guest") && t.rated) {
            return DSGArenaRequestEndedEvent.GUEST_RATED;
        }
        if (t.pending.containsKey(requester)) {
            return DSGArenaRequestEndedEvent.DUPLICATE;
        }
        if (t.blocked.contains(requester)) {
            return DSGArenaRequestEndedEvent.BLOCKED;
        }
        return null;
    }

    private boolean isSeatedAnywhere(String player) {
        for (TableMemory t : tables.values()) {
            if (t.players.contains(player)) {
                return true;
            }
        }
        return false;
    }

    private SortedSet<Integer> pendingTablesOf(String requester) {
        SortedSet<Integer> mine = new TreeSet<>();
        for (Map.Entry<Integer, TableMemory> e : tables.entrySet()) {
            if (e.getValue().pending.containsKey(requester)) {
                mine.add(e.getKey());
            }
        }
        return mine;
    }

    private void notifyMyRequests(String requester) {
        SortedSet<Integer> mine = pendingTablesOf(requester);
        int[] out = new int[mine.size()];
        int i = 0;
        for (int table : mine) {
            out[i++] = table;
        }
        notifier.send(requester, new DSGArenaMyRequestsEvent(out));
    }

    private void notifySnapshot(int table, TableMemory t, String to) {
        if (to == null) {
            return;
        }
        List<DSGArenaJoinRequestsEvent.Request> list = new ArrayList<>();
        if (t != null) {
            for (Map.Entry<String, Long> e : t.pending.entrySet()) {
                list.add(new DSGArenaJoinRequestsEvent.Request(e.getKey(), e.getValue()));
            }
        }
        notifier.send(to, new DSGArenaJoinRequestsEvent(table, list));
    }

    private void notifyEmptySnapshot(int table, String to) {
        notifySnapshot(table, null, to);
    }

    private void notifyEnded(String to, int table, TableMemory t, String requester, String reason) {
        String owner = t == null || t.owner == null ? "" : t.owner;
        notifier.send(to, new DSGArenaRequestEndedEvent(requester, table, owner, reason));
    }
}
