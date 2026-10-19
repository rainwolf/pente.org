package org.pente.gameServer.kids;

import java.util.List;

/** Per-key line buffers for live-chat copies. */
public interface ChatCopyStore {

    void append(String key, String line, int ttlSeconds);

    /** All lines in order; deletes the key. */
    List<String> drain(String key);

    /** Puts drained lines back ahead of anything appended since, in order, and resets the TTL. */
    void restore(String key, List<String> lines, int ttlSeconds);
}
