package org.pente.gameServer.server.test;

import org.pente.gameServer.event.*;
import org.pente.gameServer.server.ArenaJoinRequestRegistry;
import org.pente.gameServer.server.DSGEventToPlayerRouter;

import java.util.*;

/**
 * Records every event the join-request registry or an arena table sends, in
 * order, with its recipient. Stands in for the player router in unit tests.
 */
public class ArenaEventLog implements ArenaJoinRequestRegistry.Notifier, DSGEventToPlayerRouter {

    private final List<String> recipients = new ArrayList<>();
    private final List<DSGEvent> events = new ArrayList<>();

    public synchronized void send(String player, DSGEvent event) {
        recipients.add(player);
        events.add(event);
    }

    public void routeEvent(DSGEvent dsgEvent, String name) {
        send(name, dsgEvent);
    }

    public void addRoute(DSGEventListener dsgEventListener, String name) {
    }

    public DSGEventListener removeRoute(String name) {
        return null;
    }

    public DSGEventListener getRoute(String name) {
        return null;
    }

    public synchronized void clear() {
        recipients.clear();
        events.clear();
    }

    /** every event of this type sent to player, oldest first */
    public synchronized <E extends DSGEvent> List<E> to(String player, Class<E> type) {
        List<E> out = new ArrayList<>();
        for (int i = 0; i < events.size(); i++) {
            if (player.equals(recipients.get(i)) && type.isInstance(events.get(i))) {
                out.add(type.cast(events.get(i)));
            }
        }
        return out;
    }

    /** how many events of this type were sent to anyone */
    public synchronized int countAll(Class<? extends DSGEvent> type) {
        int n = 0;
        for (DSGEvent e : events) {
            if (type.isInstance(e)) {
                n++;
            }
        }
        return n;
    }

    /** requester names in the newest snapshot owner got for table, or null if none */
    public synchronized List<String> lastSnapshot(String owner, int table) {
        DSGArenaJoinRequestsEvent last = lastSnapshotEvent(owner, table);
        if (last == null) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (DSGArenaJoinRequestsEvent.Request r : last.getRequests()) {
            names.add(r.getPlayer());
        }
        return names;
    }

    /** seqs in the newest snapshot owner got for table, or null if none */
    public synchronized List<Long> lastSnapshotSeqs(String owner, int table) {
        DSGArenaJoinRequestsEvent last = lastSnapshotEvent(owner, table);
        if (last == null) {
            return null;
        }
        List<Long> seqs = new ArrayList<>();
        for (DSGArenaJoinRequestsEvent.Request r : last.getRequests()) {
            seqs.add(r.getSeq());
        }
        return seqs;
    }

    /** tables in the newest my-requests event player got, or null if none */
    public synchronized List<Integer> lastMyRequests(String player) {
        List<DSGArenaMyRequestsEvent> all = to(player, DSGArenaMyRequestsEvent.class);
        if (all.isEmpty()) {
            return null;
        }
        List<Integer> out = new ArrayList<>();
        for (int t : all.get(all.size() - 1).getTables()) {
            out.add(t);
        }
        return out;
    }

    /** "REASON@table" for every request-ended notice player got, oldest first */
    public synchronized List<String> reasons(String player) {
        List<String> out = new ArrayList<>();
        for (DSGArenaRequestEndedEvent e : to(player, DSGArenaRequestEndedEvent.class)) {
            out.add(e.getReason() + "@" + e.getTable());
        }
        return out;
    }

    private DSGArenaJoinRequestsEvent lastSnapshotEvent(String owner, int table) {
        List<DSGArenaJoinRequestsEvent> all = to(owner, DSGArenaJoinRequestsEvent.class);
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).getTable() == table) {
                return all.get(i);
            }
        }
        return null;
    }
}
