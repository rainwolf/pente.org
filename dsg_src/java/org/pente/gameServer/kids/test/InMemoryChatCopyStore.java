package org.pente.gameServer.kids.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.pente.gameServer.kids.ChatCopyStore;

/** Test fake: lists kept in a map (TTL ignored), with a failure switch. */
public class InMemoryChatCopyStore implements ChatCopyStore {

    private final Map<String, List<String>> lists = new HashMap<String, List<String>>();
    public boolean fail;

    private void check() {
        if (fail) {
            throw new RuntimeException("copy store failed (test)");
        }
    }

    public synchronized void append(String key, String line, int ttlSeconds) {
        check();
        List<String> l = lists.get(key);
        if (l == null) {
            l = new ArrayList<String>();
            lists.put(key, l);
        }
        l.add(line);
    }

    public synchronized List<String> drain(String key) {
        check();
        List<String> l = lists.remove(key);
        return l == null ? new ArrayList<String>() : l;
    }

    public synchronized void restore(String key, List<String> lines, int ttlSeconds) {
        check();
        if (lines == null || lines.isEmpty()) {
            return;
        }
        List<String> l = new ArrayList<String>(lines);
        List<String> since = lists.get(key);
        if (since != null) {
            l.addAll(since);
        }
        lists.put(key, l);
    }

    /** A copy; empty when absent. */
    public synchronized List<String> lines(String key) {
        List<String> l = lists.get(key);
        return l == null ? new ArrayList<String>() : new ArrayList<String>(l);
    }
}
