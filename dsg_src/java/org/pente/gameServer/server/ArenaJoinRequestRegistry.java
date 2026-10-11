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
        /** requesters whose claim a fill released while their join was still queued (R6) */
        final Set<String> releasedJoins = new HashSet<>();
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
        boolean created = t == null;
        if (created) {
            t = new TableMemory();
            tables.put(table, t);
        }
        String oldOwner = t.owner;
        boolean wasOpen = t.open;
        int oldCount = t.players.size();

        t.owner = owner;
        t.rated = rated;
        t.players = new HashSet<>(players);
        t.open = t.players.size() == 1 && noGameInProgress;

        // a player who becomes owner gets the list for their table
        boolean snapshotOwner = owner != null && !owner.equals(oldOwner);
        if (oldCount < 2 && t.players.size() >= 2) {
            // R6: the table is full
            snapshotOwner |= !t.pending.isEmpty();
            wipeMemory(table, t);
        } else if (!created && !wasOpen && t.open) {
            // R6b: not open -> open. Requests only exist while a table is
            // open, so pending is already empty; the owner starts fresh.
            wipeMemory(table, t);
            snapshotOwner = true;
        }
        if (snapshotOwner && owner != null) {
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

    /**
     * R5: accept only claims. Runs queueJoin, which queues the
     * DSGJoinTableEvent on the table's own pump, inside the lock. Always
     * answers the sender with a snapshot (R11).
     *
     * @return true if the requester was claimed and the join queued
     */
    public synchronized boolean accept(int table, String sender, String requester, Runnable queueJoin) {
        TableMemory t = tables.get(table);
        if (t == null || !sender.equals(t.owner)) {
            log4j.warn("Arena accept at table " + table + " by non-owner " + sender + " ignored");
            notifyEmptySnapshot(table, sender);
            return false;
        }
        if (t.claimed != null) {
            // an earlier accept's join is queued; the table fills when it lands
            notifySnapshot(table, t, sender);
            return false;
        }
        if (requester == null || !t.pending.containsKey(requester) || claims.containsKey(requester)) {
            // "" for a frame without playerToAccept: Gson would drop a null player
            notifier.send(sender, new DSGArenaRequestEndedEvent(
                    requester == null ? "" : requester, table, t.owner,
                    DSGArenaRequestEndedEvent.NO_LONGER_AVAILABLE));
            notifySnapshot(table, t, sender);
            return false;
        }
        t.pending.remove(requester);
        t.claimed = requester;
        claims.put(requester, table);
        queueJoin.run();
        notifySnapshot(table, t, sender);
        notifyMyRequests(requester);
        return true;
    }

    /**
     * R6: may this join land? Only a claimed join is checked: the requester
     * must still be in the main room and not seated at another table.
     * Otherwise the claim is released and the owner told. The one queued
     * join whose claim a fill already ended with TABLE_FULL is refused
     * silently, so the requester is not seated as a spectator after that
     * notice.
     */
    public synchronized boolean admitJoin(int table, String player, boolean playerInMainRoom) {
        TableMemory t = tables.get(table);
        if (t != null && t.releasedJoins.remove(player)) {
            return false;
        }
        Integer claimedTable = claims.get(player);
        if (claimedTable == null || claimedTable != table) {
            return true;
        }
        if (playerInMainRoom && !isSeatedElsewhere(player, table)) {
            return true;
        }
        releaseClaimAndTellOwner(table, player);
        return false;
    }

    /**
     * The join was admitted but ServerTable.handleJoin did not seat the
     * player (BOOTED within 5 minutes, or it threw). A claim held at this
     * table is released and the owner told, so neither stays stuck on it.
     */
    public synchronized void joinFailed(int table, String player) {
        Integer claimedTable = claims.get(player);
        if (claimedTable != null && claimedTable == table) {
            releaseClaimAndTellOwner(table, player);
        }
    }

    /**
     * R7: player sat down at table, so their requests everywhere end
     * silently. Fulfils their claim at this table; a claim at another table
     * is kept so that table refuses the join when it lands (R6).
     */
    public synchronized void playerJoinedTable(int table, String player) {
        Integer claimedTable = claims.get(player);
        if (claimedTable != null && claimedTable == table) {
            claims.remove(player);
            TableMemory t = tables.get(table);
            if (t != null) {
                t.claimed = null;
            }
        }
        if (removeAllPending(player)) {
            notifyMyRequests(player);
        }
    }

    /**
     * R7: the player left the main room (or disconnected); their requests
     * end silently. A claim is kept so the queued join is refused (R6).
     */
    public synchronized void requesterLeftMainRoom(String player) {
        if (removeAllPending(player)) {
            notifyMyRequests(player);
        }
    }

    /**
     * R8: the table is gone. Pending and claimed requesters get TABLE_CLOSED
     * and the memory is purged, so a reused table number starts clean.
     */
    public synchronized void tableRemoved(int table) {
        TableMemory t = tables.remove(table);
        if (t == null) {
            return;
        }
        endAllPending(table, t, DSGArenaRequestEndedEvent.TABLE_CLOSED);
        releaseClaim(table, t, DSGArenaRequestEndedEvent.TABLE_CLOSED);
    }

    /**
     * R2/R11: an arena event for a table number that no longer exists still
     * gets its answer. Called by ArenaServer.routeEventToTable.
     */
    public synchronized void answerUnroutable(DSGEvent event, int table) {
        if (event instanceof DSGArenaRequestJoinTableEvent request) {
            notifier.send(request.getPlayer(), new DSGArenaRequestEndedEvent(
                    request.getPlayer(), table, "", DSGArenaRequestEndedEvent.NOT_AVAILABLE));
            notifyMyRequests(request.getPlayer());
        } else if (event instanceof DSGArenaWithdrawJoinRequestEvent withdraw) {
            notifyMyRequests(withdraw.getPlayer());
        } else if (event instanceof DSGArenaAcceptTableJoinEvent
                || event instanceof DSGArenaRejectTableJoinEvent) {
            notifyEmptySnapshot(table, ((DSGTableEvent) event).getPlayer());
        }
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

    public synchronized Integer claimedTable(String requester) {
        return claims.get(requester);
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

    private boolean isSeatedElsewhere(String player, int table) {
        for (Map.Entry<Integer, TableMemory> e : tables.entrySet()) {
            if (e.getKey() != table && e.getValue().players.contains(player)) {
                return true;
            }
        }
        return false;
    }

    /** R6/R6b: end pending with TABLE_FULL, release any claim, forget the blocked set. */
    private void wipeMemory(int table, TableMemory t) {
        endAllPending(table, t, DSGArenaRequestEndedEvent.TABLE_FULL);
        String released = t.claimed;
        releaseClaim(table, t, DSGArenaRequestEndedEvent.TABLE_FULL);
        t.blocked.clear();
        if (released != null) {
            // its DSGJoinTableEvent is still queued on the table's pump
            t.releasedJoins.add(released);
        }
    }

    /** R6: the claimed join will not seat the requester; the owner gets NO_LONGER_AVAILABLE. */
    private void releaseClaimAndTellOwner(int table, String player) {
        claims.remove(player);
        TableMemory t = tables.get(table);
        if (t != null) {
            t.claimed = null;
            if (t.owner != null) {
                notifier.send(t.owner, new DSGArenaRequestEndedEvent(
                        player, table, t.owner, DSGArenaRequestEndedEvent.NO_LONGER_AVAILABLE));
                notifySnapshot(table, t, t.owner);
            }
        }
    }

    /** Ends every pending request at t with a notice; each requester gets my-requests. */
    private void endAllPending(int table, TableMemory t, String reason) {
        List<String> ended = new ArrayList<>(t.pending.keySet());
        t.pending.clear();
        for (String requester : ended) {
            notifyEnded(requester, table, t, requester, reason);
            notifyMyRequests(requester);
        }
    }

    private void releaseClaim(int table, TableMemory t, String reason) {
        if (t.claimed == null) {
            return;
        }
        String requester = t.claimed;
        t.claimed = null;
        claims.remove(requester);
        notifyEnded(requester, table, t, requester, reason);
    }

    /** Removes requester's pending requests everywhere; owners get snapshots. */
    private boolean removeAllPending(String requester) {
        boolean changed = false;
        for (Map.Entry<Integer, TableMemory> e : tables.entrySet()) {
            TableMemory t = e.getValue();
            if (t.pending.remove(requester) != null) {
                changed = true;
                notifySnapshot(e.getKey(), t, t.owner);
            }
        }
        return changed;
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
