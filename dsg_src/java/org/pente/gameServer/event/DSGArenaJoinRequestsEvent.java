package org.pente.gameServer.event;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Server to owner: the full pending request list for one arena table,
 * ordered by seq ascending. The list is copied on construction because Gson
 * serializes the event later, on the player's writer thread.
 */
public class DSGArenaJoinRequestsEvent extends AbstractDSGTableEvent {

    public static class Request implements Serializable {

        private String player;
        private long seq;

        public Request(String player, long seq) {
            this.player = player;
            this.seq = seq;
        }

        public String getPlayer() {
            return player;
        }

        public long getSeq() {
            return seq;
        }
    }

    private List<Request> requests;

    public DSGArenaJoinRequestsEvent(int table, List<Request> requests) {
        super(null, table);
        this.requests = new ArrayList<>(requests);
    }

    public List<Request> getRequests() {
        return Collections.unmodifiableList(requests);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("Arena join requests table " + getTable() + ":");
        for (Request r : requests) {
            sb.append(' ').append(r.player).append('#').append(r.seq);
        }
        return sb.toString();
    }
}
