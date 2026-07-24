package org.pente.webdb;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.pente.webdb.dto.PositionStatsRequest;
import org.pente.webdb.dto.PositionStatsResponse;

/**
 * Bounded, TTL'd LRU cache for <em>archive-scope</em> position statistics
 * ({@code POST /api/db/position-stats} with {@code scope="archive"}).
 *
 * <p>The archive is immutable within a request's lifetime and the same opening
 * positions are queried repeatedly, so the (relatively expensive) grouped
 * {@code pente_move} aggregation is worth caching. Only pure-archive requests
 * are cached; {@code scope="mine"} and {@code scope="both"} never read or write
 * this cache (see {@link PositionStatsHandler}).
 *
 * <h2>Eviction</h2>
 * Backed by a {@link LinkedHashMap} in access order with a
 * {@link #removeEldestEntry} override, so once the map exceeds
 * {@link #MAX_ENTRIES} the least-recently-used entry is dropped. Every entry
 * also carries a creation timestamp; an entry older than {@link #TTL_MILLIS} is
 * treated as a miss and evicted lazily on the next lookup that touches it.
 *
 * <h2>Thread-safety</h2>
 * The servlet container calls handlers concurrently, so every map operation is
 * guarded by {@code synchronized} (the {@link LinkedHashMap} is not otherwise
 * safe, and access-order mutates the structure even on {@code get}). Cached
 * {@link PositionStatsResponse} objects are treated as read-only (they are only
 * ever JSON-serialized), so the same instance is shared across hits.
 *
 * <h2>Cache key</h2>
 * See {@link Key}. Beyond the obvious {@code (game, hash, moveNum, filters)},
 * the key also pins the caller's board {@code rotation}: two orientations of the
 * same physical position share the canonical {@code hash} but yield different
 * responses (their {@code nextMoves} are mapped into <em>that caller's</em>
 * orientation and {@code resp.rotation} differs), so keying on {@code hash}
 * alone would serve one orientation's moves for the other. Filter fields are
 * compared verbatim (null-safe) via value-object {@code equals}/{@code hashCode}
 * rather than a concatenated string, which is conservative — whitespace/case
 * variants simply miss instead of ever risking a wrong-scope hit — and free of
 * delimiter-collision hazards.
 */
final class StatsCache {

    /** Hard ceiling on cached entries; LRU eviction past this. */
    static final int MAX_ENTRIES = 10_000;

    /** Per-entry time-to-live: one hour. */
    static final long TTL_MILLIS = 60L * 60L * 1000L;

    private static final class Entry {
        final PositionStatsResponse value;
        final long createdAtMillis;

        Entry(PositionStatsResponse value, long createdAtMillis) {
            this.value = value;
            this.createdAtMillis = createdAtMillis;
        }
    }

    private final long ttlMillis;
    private final Map<Key, Entry> map;

    /** Package-visible hit counter (exposed for tests via the handler). */
    private int hits;

    StatsCache() {
        this(TTL_MILLIS, MAX_ENTRIES);
    }

    /** Seam for tests that need a short TTL or a small capacity. */
    StatsCache(final long ttlMillis, final int maxEntries) {
        this.ttlMillis = ttlMillis;
        this.map = new LinkedHashMap<Key, Entry>(16, 0.75f, true) {
            protected boolean removeEldestEntry(Map.Entry<Key, Entry> eldest) {
                return size() > maxEntries;
            }
        };
    }

    /**
     * Return the cached response for {@code key}, or {@code null} on a miss.
     * A present-but-expired entry is evicted and reported as a miss. A hit
     * bumps the entry to most-recently-used and increments {@link #hitCount}.
     */
    synchronized PositionStatsResponse get(Key key) {
        Entry e = map.get(key);
        if (e == null) {
            return null;
        }
        if (System.currentTimeMillis() - e.createdAtMillis > ttlMillis) {
            map.remove(key);
            return null;
        }
        hits++;
        return e.value;
    }

    /** Insert (or refresh) the response for {@code key}, timestamped now. */
    synchronized void put(Key key, PositionStatsResponse value) {
        map.put(key, new Entry(value, System.currentTimeMillis()));
    }

    /** Current number of cache hits since construction. */
    synchronized int hitCount() {
        return hits;
    }

    /** Number of live entries (test/diagnostic helper). */
    synchronized int size() {
        return map.size();
    }

    /** Drop all entries and reset the hit counter (test helper). */
    synchronized void clear() {
        map.clear();
        hits = 0;
    }

    /**
     * Build a cache key from the archive request's discriminating inputs. The
     * filter DTO is read verbatim (null-safe); passing {@code null} filters is
     * equivalent to a filter object with all fields at their defaults.
     */
    static Key keyOf(int game, long hash, int moveNum, int rotation,
                     PositionStatsRequest.Filters f) {
        return new Key(game, hash, moveNum, rotation, f);
    }

    /**
     * Immutable, value-equal cache key. Every field that can change the archive
     * response participates in {@code equals}/{@code hashCode}. Filter strings
     * are stored exactly as received (no trimming/lower-casing), so equality is
     * strictly the "same request" relation — conservative but never wrong.
     */
    static final class Key {

        private final int game;
        private final long hash;
        private final int moveNum;
        private final int rotation;

        private final String player1Name;
        private final int player1Seat;
        private final String player2Name;
        private final int player2Seat;
        private final String site;
        private final String event;
        private final String round;
        private final String section;
        private final String afterDate;
        private final String beforeDate;
        private final int winner;

        Key(int game, long hash, int moveNum, int rotation,
            PositionStatsRequest.Filters f) {
            this.game = game;
            this.hash = hash;
            this.moveNum = moveNum;
            this.rotation = rotation;
            if (f == null) {
                this.player1Name = null;
                this.player1Seat = 0;
                this.player2Name = null;
                this.player2Seat = 0;
                this.site = null;
                this.event = null;
                this.round = null;
                this.section = null;
                this.afterDate = null;
                this.beforeDate = null;
                this.winner = 0;
            } else {
                this.player1Name = f.player1Name;
                this.player1Seat = f.player1Seat;
                this.player2Name = f.player2Name;
                this.player2Seat = f.player2Seat;
                this.site = f.site;
                this.event = f.event;
                this.round = f.round;
                this.section = f.section;
                this.afterDate = f.afterDate;
                this.beforeDate = f.beforeDate;
                this.winner = f.winner;
            }
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key)) {
                return false;
            }
            Key k = (Key) o;
            return game == k.game
                    && hash == k.hash
                    && moveNum == k.moveNum
                    && rotation == k.rotation
                    && player1Seat == k.player1Seat
                    && player2Seat == k.player2Seat
                    && winner == k.winner
                    && Objects.equals(player1Name, k.player1Name)
                    && Objects.equals(player2Name, k.player2Name)
                    && Objects.equals(site, k.site)
                    && Objects.equals(event, k.event)
                    && Objects.equals(round, k.round)
                    && Objects.equals(section, k.section)
                    && Objects.equals(afterDate, k.afterDate)
                    && Objects.equals(beforeDate, k.beforeDate);
        }

        public int hashCode() {
            return Objects.hash(Integer.valueOf(game), Long.valueOf(hash),
                    Integer.valueOf(moveNum), Integer.valueOf(rotation),
                    player1Name, Integer.valueOf(player1Seat),
                    player2Name, Integer.valueOf(player2Seat),
                    site, event, round, section, afterDate, beforeDate,
                    Integer.valueOf(winner));
        }
    }
}
