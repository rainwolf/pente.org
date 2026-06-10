package org.pente.gameServer.server;

import org.apache.log4j.Category;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.providers.PooledConnectionProvider;

import java.io.*;
import java.time.Duration;
import java.util.*;

/**
 * Manages a shared UnifiedJedis instance for the application.
 * <p>
 * Configuration is read from environment variables:
 * REDIS_HOST     (default: localhost)
 * REDIS_PORT     (default: 6379)
 * REDIS_PASSWORD (default: none)
 * <p>
 * Lifecycle: initialize once in DSGContextListener.contextInitialized(),
 * shut down in contextDestroyed() via destroy().
 * <p>
 * Cache namespaces (used as Redis hash keys so entire subsets can be
 * invalidated with a single DEL command):
 * <pre>
 *   RedisConnectionManager r = RedisConnectionManager.getInstance();
 *
 *   // store / retrieve
 *   r.hput(RedisConnectionManager.PID_TO_PLAYER, pid, playerData);
 *   DSGPlayerData p = r.hget(RedisConnectionManager.PID_TO_PLAYER, pid, DSGPlayerData.class);
 *
 *   // remove one entry
 *   r.hremove(RedisConnectionManager.PID_TO_PLAYER, pid);
 *
 *   // invalidate all cached players at once
 *   r.invalidate(RedisConnectionManager.PID_TO_PLAYER);
 * </pre>
 */
public class RedisConnectionManager {

    private static final Category log4j =
            Category.getInstance(RedisConnectionManager.class.getName());

    private static RedisConnectionManager instance;

    private final UnifiedJedis jedis;

    // -------------------------------------------------------------------------
    // Availability circuit breaker
    //
    // Redis is a best-effort cache; MariaDB (reached via each storer's
    // baseStorer) is the source of truth. When Redis is unreachable, reads
    // report a cache miss and writes are skipped, so callers fall through to
    // the DB. There is deliberately NO in-memory fallback store — a second
    // cache that diverges from Redis is exactly what produced the
    // post-recovery stale-read bugs. The flag exists only to fast-fail during
    // an outage instead of blocking every request on Jedis's connect/retry
    // timeout (which would exhaust the Tomcat thread pool).
    // -------------------------------------------------------------------------

    /**
     * True while Redis is healthy; set to false on first error, restored by the recovery probe.
     */
    private volatile boolean redisAvailable = true;

    /**
     * Background timer used for recovery probes; null for no-op/test instances.
     */
    private final Timer recoveryTimer;

    private static final long RECOVERY_INTERVAL_MS = 30_000;

    // -------------------------------------------------------------------------
    // Cache namespace constants
    // Name format: <key-type>_TO_<value-type> for maps,
    //              <entity>_LIST_<qualifier>   for singleton lists.
    // Each constant is the Redis hash key for that cache subset.
    // -------------------------------------------------------------------------

    /**
     * CacheDSGPlayerStorer: pid (long) → DSGPlayerData
     */
    public static final String PID_TO_PLAYER = "pid:player";

    /**
     * CacheDSGPlayerStorer: lowercase name (String) → DSGPlayerData
     */
    public static final String NAME_TO_PLAYER = "name:player";

    /**
     * CacheDSGPlayerStorer: pid (long) → List<DSGPlayerPreference>
     */
    public static final String PID_TO_PREFS = "pid:prefs";

    /**
     * CacheDSGPlayerStorer: pid (long) → List<DSGIgnoreData>
     */
    public static final String PID_TO_IGNORES = "pid:ignores";

    public static final String PID_TO_AVATAR = "pid:avatar";

    /**
     * CacheDSGFollowerStorer: pid (long) → List<Long> followers
     */
    public static final String PID_TO_FOLLOWERS = "pid:followers";

    /**
     * CacheDSGFollowerStorer: pid (long) → List<Long> following
     */
    public static final String PID_TO_FOLLOWING = "pid:following";

    /**
     * CacheTourneyStorer: eid (int) → Tourney
     */
    public static final String EID_TO_TOURNEY = "eid:tourney";

    /**
     * CacheTourneyStorer: eid (int) → List<Long> player pids
     */
    public static final String EID_TO_TOURNEY_PLAYER_PIDS = "eid:tourney_player_pids";

    /**
     * CacheTourneyStorer: singleton — List<Tourney> upcoming
     */
    public static final String TOURNEY_LIST_UPCOMING = "tourney_list:upcoming";

    /**
     * CacheTourneyStorer: singleton — List<Tourney> current
     */
    public static final String TOURNEY_LIST_CURRENT = "tourney_list:current";

    /**
     * CacheTourneyStorer: singleton — List<Tourney> completed
     */
    public static final String TOURNEY_LIST_COMPLETED = "tourney_list:completed";

    /**
     * CacheTBStorer: gid (long) → TBGame
     */
    public static final String GID_TO_TB_GAME = "gid:tb_game";

    /**
     * CacheTBStorer: sid (long) → TBSet
     */
    public static final String SID_TO_TB_SET = "sid:tb_set";

    /**
     * CacheTBStorer: pid (long) → HashSet<Long> set-ids
     */
    public static final String PID_TO_TB_SET_IDS = "pid:tb_set_ids";

    /**
     * CacheTBStorer: eid (int) → tb-eid (int) mapping
     */
    public static final String EID_TO_TB_EID = "eid:tb_eid";

    /**
     * CacheTBStorer: pid (long) → TBVacation
     */
    public static final String PID_TO_TB_VACATION = "pid:tb_vacation";

    /**
     * gid -> sid index (aggregate-root lookup, replaces GID_TO_TB_GAME)
     */
    public static final String GID_TO_SID = "gid:tb_sid";

    /**
     * set of sids that are currently waiting (invitations)
     */
    public static final String TB_WAITING_SET_IDS = "tb:waiting_set_ids";

    /**
     * CacheKOTHStorer: game (int) → Hill
     */
    public static final String GAME_TO_KOTH_HILL = "game:koth_hill";

    /**
     * CacheKOTHStorer: game (int) → eid (int)
     */
    public static final String GAME_TO_KOTH_EID = "game:koth_eid";

    /**
     * Reserved hash field marking a namespace as fully bootstrapped from the DB
     * (see {@link #isLoaded}/{@link #markLoaded}). It lives INSIDE the namespace
     * hash it guards — not in a separate hash — so it is created, evicted (under
     * maxmemory), and deleted (by {@link #invalidate}) atomically with the data.
     * A separate sentinel hash could be LRU-evicted while the data survived (or
     * vice versa), pinning a list cache to a wrong loaded/empty state; co-located
     * it cannot. The name cannot collide with real field names
     * (numeric ids, "list"); {@link #hgetAllFields}/{@link #hgetAllValues} filter
     * it out so callers iterating a namespace never see it.
     */
    public static final String LOADED_FIELD = "__loaded__";

    // -------------------------------------------------------------------------

    /**
     * For testing only — creates an unconnected, no-op instance.
     */
    protected RedisConnectionManager() {
        this.jedis = null;
        this.recoveryTimer = null;
    }

    /**
     * For testing only — installs a custom instance (e.g. an in-memory fake).
     */
    public static synchronized void setInstance(RedisConnectionManager instance) {
        RedisConnectionManager.instance = instance;
    }

    /**
     * For testing only — removes the current instance.
     */
    public static synchronized void resetInstance() {
        if (instance != null && instance.jedis != null) {
            instance.jedis.close();
        }
        instance = null;
    }

    private RedisConnectionManager(String host, int port, String password) {
        DefaultJedisClientConfig.Builder clientConfigBuilder = DefaultJedisClientConfig.builder()
                .socketTimeoutMillis(2000)
                .connectionTimeoutMillis(2000);
        if (password != null && !password.isEmpty()) {
            clientConfigBuilder.password(password);
        }
        JedisClientConfig clientConfig = clientConfigBuilder.build();

        PooledConnectionProvider provider = new PooledConnectionProvider(
                new HostAndPort(host, port), clientConfig);
        jedis = new UnifiedJedis(provider, 3, Duration.ofSeconds(10));
        recoveryTimer = new Timer("redis-recovery", true);

        log4j.info("RedisConnectionManager initialized: " + host + ":" + port);
    }

    /**
     * Initialize the singleton from environment variables.
     * Must be called once before getInstance().
     *
     * @return
     */
    public static synchronized RedisConnectionManager initialize() {
        if (instance != null) {
            log4j.warn("RedisConnectionManager.initialize() called more than once, ignoring.");
            return null;
        }

        String host = getEnv("REDIS_HOST", "localhost");
        int port = Integer.parseInt(getEnv("REDIS_PORT", "6379"));
        String password = getEnv("REDIS_PASSWORD", null);
        log4j.info("Initializing RedisConnectionManager with host=" + host + ", port=" + port +
                (password != null ? ", password=******" : ", no password"));
        instance = new RedisConnectionManager(host, port, password);
        return instance;
    }

    public static synchronized RedisConnectionManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("RedisConnectionManager not initialized. Call initialize() first.");
        }
        return instance;
    }

    /**
     * Returns the shared UnifiedJedis instance. Thread-safe — call methods directly.
     */
    public UnifiedJedis getJedis() {
        return jedis;
    }

    /**
     * True when Redis is healthy. Callers whose read CANNOT distinguish an empty
     * cache from a down cache — the list-bootstrap getters, where an empty
     * {@code hgetAllFields} during an outage would be served as an empty list
     * rather than re-loaded from the DB — must consult this and serve straight
     * from the source of truth when it returns false.
     */
    public boolean isAvailable() {
        return jedis == null || redisAvailable;
    }

    /**
     * True once {@link #markLoaded} has run for this namespace and the marker
     * has not since been evicted/deleted. Used by the list-bootstrap caches to
     * tell "already loaded, legitimately empty" from "never loaded" — a plain
     * empty hash cannot be told apart from a missing one.
     */
    public boolean isLoaded(String namespace) {
        return hexists(namespace, LOADED_FIELD);
    }

    /**
     * Mark a namespace as bootstrapped by writing the reserved {@link #LOADED_FIELD}
     * into its hash. Cleared automatically by {@link #invalidate} (same key) and
     * by eviction/flush.
     */
    public void markLoaded(String namespace) {
        hput(namespace, LOADED_FIELD, Boolean.TRUE);
    }

    /**
     * Close the pool. Call from DSGContextListener.contextDestroyed().
     */
    public void destroy() {
        if (recoveryTimer != null) {
            recoveryTimer.cancel();
        }
        if (jedis != null) {
            jedis.close();
            log4j.info("RedisConnectionManager pool closed.");
        }
        instance = null;
    }

    // -------------------------------------------------------------------------
    // Convenience methods — hash-based cache operations
    //
    // Each namespace is a Redis hash:  HSET namespace field serialized_value
    // Invalidate one entry:            HDEL namespace field
    // Invalidate entire subset:        DEL  namespace
    //
    // When Redis is unavailable, reads report a cache miss and writes are
    // skipped; the caller falls through to the DB. A background probe resumes
    // Redis as soon as it recovers.
    // -------------------------------------------------------------------------

    /**
     * Store a Serializable value under namespace[field]. A no-op when Redis is
     * unavailable — the storer's DB write-through is the source of truth.
     */
    public <T extends Serializable> void hput(String namespace, String field, T value) {
        if (value == null) return;
        if (jedis != null && redisAvailable) {
            byte[] bytes = serialize(value); // throws RuntimeException on serialization failure — not a Redis failure
            try {
                jedis.hset(namespace.getBytes(), field.getBytes(), bytes);
                // no TTL: namespaces are write-through source-of-truth caches; expiry
                // strands the LOADED_FIELD markers and empties list reads
                log4j.debug("CACHE WRITE [redis]  " + namespace + "[" + field + "] (" + value.getClass().getSimpleName() + ")");
            } catch (Exception e) {
                handleRedisFailure(e);
            }
        }
    }

    /**
     * Store a Serializable value under namespace[pid].
     */
    public <T extends Serializable> void hput(String namespace, long field, T value) {
        hput(namespace, Long.toString(field), value);
    }

    /**
     * Store a Serializable value under namespace[eid/game].
     */
    public <T extends Serializable> void hput(String namespace, int field, T value) {
        hput(namespace, Integer.toString(field), value);
    }

    /**
     * Retrieve a value from namespace[field], or null if absent.
     * The caller is responsible for casting to the expected type.
     */
    @SuppressWarnings("unchecked")
    public <T> T hget(String namespace, String field) {
        if (jedis != null && redisAvailable) {
            try {
                byte[] bytes = jedis.hget(namespace.getBytes(), field.getBytes());
                if (bytes != null) {
                    T val = (T) deserialize(bytes); // throws RuntimeException on deserialization failure — not a Redis failure
                    log4j.debug("CACHE HIT  [redis]  " + namespace + "[" + field + "] (" + val.getClass().getSimpleName() + ")");
                    return val;
                }
                log4j.debug("CACHE MISS [redis]  " + namespace + "[" + field + "]");
            } catch (Exception e) {
                handleRedisFailure(e);
            }
        }
        // Redis unavailable: report a miss so the caller reloads from the DB.
        return null;
    }

    /**
     * Retrieve a value from namespace[pid].
     */
    public <T> T hget(String namespace, long field) {
        return hget(namespace, Long.toString(field));
    }

    /**
     * Retrieve a value from namespace[eid/game].
     */
    public <T> T hget(String namespace, int field) {
        return hget(namespace, Integer.toString(field));
    }

    /**
     * Returns true if namespace[field] exists in Redis.
     */
    public boolean hexists(String namespace, String field) {
        if (jedis != null && redisAvailable) {
            try {
                return jedis.hexists(namespace.getBytes(), field.getBytes());
            } catch (Exception e) {
                handleRedisFailure(e);
            }
        }
        // Redis unavailable: report absent so a loaded-sentinel check re-bootstraps from the DB.
        return false;
    }

    /**
     * Returns true if namespace[pid] exists in Redis.
     */
    public boolean hexists(String namespace, long field) {
        return hexists(namespace, Long.toString(field));
    }

    /**
     * Remove a single entry from the cache namespace.
     */
    public void hremove(String namespace, String field) {
        if (jedis != null && redisAvailable) {
            try {
                jedis.hdel(namespace.getBytes(), field.getBytes());
                log4j.debug("CACHE EVICT [redis]  " + namespace + "[" + field + "]");
            } catch (Exception e) {
                handleRedisFailure(e);
            }
        }
        // Redis unavailable: nothing cached to evict.
    }

    /**
     * Remove a single pid-keyed entry from the cache namespace.
     */
    public void hremove(String namespace, long field) {
        hremove(namespace, Long.toString(field));
    }

    /**
     * Remove a single int-keyed entry from the cache namespace.
     */
    public void hremove(String namespace, int field) {
        hremove(namespace, Integer.toString(field));
    }

    /**
     * Invalidate an entire cache subset by deleting its hash.
     * E.g. {@code invalidate(PID_TO_PLAYER)} drops all cached players.
     */
    public void invalidate(String namespace) {
        if (jedis != null && redisAvailable) {
            try {
                jedis.del(namespace);
                log4j.debug("CACHE FLUSH [redis]  " + namespace);
            } catch (Exception e) {
                handleRedisFailure(e);
            }
        }
        // Redis unavailable: nothing cached to flush.
    }

    /**
     * Return all deserialized values stored in a namespace hash.
     * Useful for iterating over all cached entries (e.g. subscription checks).
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> hgetAllValues(String namespace) {
        if (jedis != null && redisAvailable) {
            Map<byte[], byte[]> entries;
            try {
                entries = jedis.hgetAll(namespace.getBytes());
            } catch (Exception e) {
                handleRedisFailure(e);
                entries = null;
            }
            if (entries != null) {
                byte[] loadedField = LOADED_FIELD.getBytes();
                List<T> result = new ArrayList<>(entries.size());
                for (Map.Entry<byte[], byte[]> e : entries.entrySet()) {
                    if (Arrays.equals(e.getKey(), loadedField)) continue; // skip the loaded marker
                    result.add((T) deserialize(e.getValue())); // throws RuntimeException on deserialization failure — not a Redis failure
                }
                log4j.debug("CACHE SCAN  [redis]  " + namespace + " (" + result.size() + " entries)");
                return result;
            }
        }
        // Redis unavailable: report an empty scan so the caller reloads from the DB.
        return new ArrayList<>();
    }

    /**
     * Return all field names (hash keys) stored in a namespace hash.
     * Useful for iterating over all cached entry ids; empty list if none.
     */
    public List<String> hgetAllFields(String namespace) {
        if (jedis != null && redisAvailable) {
            Set<String> fields;
            try {
                fields = jedis.hkeys(namespace);
            } catch (Exception e) {
                handleRedisFailure(e);
                fields = null;
            }
            if (fields != null) {
                fields.remove(LOADED_FIELD); // never expose the loaded marker as a data field
                List<String> result = new ArrayList<String>(fields);
                log4j.debug("CACHE KEYS  [redis]  " + namespace + " (" + result.size() + " entries)");
                return result;
            }
        }
        // Redis unavailable: report no keys so the caller reloads from the DB.
        return new ArrayList<String>();
    }

    // -------------------------------------------------------------------------
    // Failover helpers
    // -------------------------------------------------------------------------

    /**
     * Called on any Redis exception. Trips the circuit breaker (so subsequent
     * calls fast-fail to the DB instead of each blocking on the connect/retry
     * timeout) and schedules a background probe that resumes Redis when it
     * recovers.
     */
    private synchronized void handleRedisFailure(Exception e) {
        if (!redisAvailable) return; // already fast-failing
        redisAvailable = false;
        log4j.error("Redis unavailable — serving from the DB until it recovers: " + e.getMessage());
        if (recoveryTimer != null) {
            recoveryTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    try {
                        jedis.ping();
                    } catch (Exception ex) {
                        return; // still down, keep probing
                    }
                    // Redis is reachable again. If it kept its data through the
                    // outage, anything mutated meanwhile (DB written, cache
                    // write skipped) is now stale in Redis. Flush so list caches
                    // re-bootstrap via their LOADED_FIELD marker and item caches reheal
                    // from the DB on miss. Best-effort: a denied FLUSHDB (e.g. a
                    // read-only replica) must not pin us in fast-fail mode, so
                    // we resume regardless and just log.
                    try {
                        jedis.flushDB();
                    } catch (Exception ex) {
                        log4j.error("Redis recovered but FLUSHDB failed; resuming with possibly stale cache: " + ex.getMessage());
                    }
                    synchronized (RedisConnectionManager.this) {
                        redisAvailable = true;
                    }
                    log4j.info("Redis recovered — resuming cache.");
                    cancel();
                }
            }, RECOVERY_INTERVAL_MS, RECOVERY_INTERVAL_MS);
        }
    }

    // -------------------------------------------------------------------------
    // Serialization helpers
    // -------------------------------------------------------------------------

    public static byte[] serialize(Serializable obj) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(obj);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Redis serialize failed for " + obj.getClass().getName() + ": " + e.getMessage(), e);
        }
    }

    public static Object deserialize(byte[] bytes) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            return ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Redis deserialize failed", e);
        }
    }

    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    public long count_cached(String namespace) {
        return jedis.hlen(namespace);
    }
}
