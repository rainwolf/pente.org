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
import java.util.concurrent.ConcurrentHashMap;

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
    // In-memory fallback (used when Redis is unreachable)
    // -------------------------------------------------------------------------

    /**
     * Mirrors Redis hash structure: namespace → (field → raw value).
     */
    private final Map<String, Map<String, Object>> fallback = new ConcurrentHashMap<>();

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
     * Sentinel hash: namespace → Boolean.TRUE once that namespace has been
     * fully bootstrapped from the DB. Lives in Redis (NOT a JVM field) so that
     * losing the cached data also loses the sentinel and the next read
     * re-bootstraps — a JVM flag survives a Redis wipe and pins list caches to
     * empty until Tomcat restarts. Deleted on failover recovery because reads
     * and writes during an outage hit the discarded in-memory fallback.
     */
    public static final String CACHE_LOADED_FLAGS = "cache:loaded_flags";

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
    // All operations fall back to an in-memory map when Redis is unavailable.
    // A background probe re-enables Redis as soon as it recovers.
    // -------------------------------------------------------------------------

    /**
     * Store a Serializable value under namespace[field].
     */
    public <T extends Serializable> void hput(String namespace, String field, T value) {
        if (value == null) return;
        if (jedis != null && redisAvailable) {
            byte[] bytes = serialize(value); // throws RuntimeException on serialization failure — not a Redis failure
            try {
                jedis.hset(namespace.getBytes(), field.getBytes(), bytes);
                // no TTL: namespaces are write-through source-of-truth caches; expiry
                // strands load-once flags (e.g. waitingSetsLoaded) and empties reads
                log4j.debug("CACHE WRITE [redis]  " + namespace + "[" + field + "] (" + value.getClass().getSimpleName() + ")");
                return;
            } catch (Exception e) {
                handleRedisFailure(e);
            }
        }
        fallbackPut(namespace, field, value);
        log4j.debug("CACHE WRITE [memory] " + namespace + "[" + field + "] (" + value.getClass().getSimpleName() + ")");
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
            byte[] bytes;
            try {
                bytes = jedis.hget(namespace.getBytes(), field.getBytes());
            } catch (Exception e) {
                handleRedisFailure(e);
                bytes = null;
            }
            if (bytes != null) {
                T val = (T) deserialize(bytes); // throws RuntimeException on deserialization failure — not a Redis failure
                log4j.debug("CACHE HIT  [redis]  " + namespace + "[" + field + "] (" + val.getClass().getSimpleName() + ")");
                return val;
            } else if (redisAvailable) {
                log4j.debug("CACHE MISS [redis]  " + namespace + "[" + field + "]");
                return null;
            }
        }
        T val = fallbackGet(namespace, field);
        if (val != null) {
            log4j.debug("CACHE HIT  [memory] " + namespace + "[" + field + "] (" + val.getClass().getSimpleName() + ")");
        } else {
            log4j.debug("CACHE MISS [memory] " + namespace + "[" + field + "]");
        }
        return val;
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
        Map<String, Object> map = fallback.get(namespace);
        return map != null && map.containsKey(field);
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
                return;
            } catch (Exception e) {
                handleRedisFailure(e);
            }
        }
        fallbackRemove(namespace, field);
        log4j.debug("CACHE EVICT [memory] " + namespace + "[" + field + "]");
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
                return;
            } catch (Exception e) {
                handleRedisFailure(e);
            }
        }
        fallback.remove(namespace);
        log4j.debug("CACHE FLUSH [memory] " + namespace);
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
                List<T> result = new ArrayList<>(entries.size());
                for (byte[] value : entries.values()) {
                    result.add((T) deserialize(value)); // throws RuntimeException on deserialization failure — not a Redis failure
                }
                log4j.debug("CACHE SCAN  [redis]  " + namespace + " (" + result.size() + " entries)");
                return result;
            }
        }
        Map<String, Object> map = fallback.get(namespace);
        List<T> result = map == null ? new ArrayList<>() : new ArrayList<>((java.util.Collection<T>) map.values());
        log4j.debug("CACHE SCAN  [memory] " + namespace + " (" + result.size() + " entries)");
        return result;
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
                List<String> result = new ArrayList<String>(fields);
                log4j.debug("CACHE KEYS  [redis]  " + namespace + " (" + result.size() + " entries)");
                return result;
            }
        }
        Map<String, Object> map = fallback.get(namespace);
        List<String> result = map == null ? new ArrayList<String>() : new ArrayList<String>(map.keySet());
        log4j.debug("CACHE KEYS  [memory] " + namespace + " (" + result.size() + " entries)");
        return result;
    }

    // -------------------------------------------------------------------------
    // Failover helpers
    // -------------------------------------------------------------------------

    /**
     * Called on any Redis exception. Switches to in-memory mode and schedules
     * a background probe that switches back when Redis recovers.
     */
    private synchronized void handleRedisFailure(Exception e) {
        if (!redisAvailable) return; // already in fallback mode
        redisAvailable = false;
        log4j.error("Redis unavailable — switching to in-memory cache fallback: " + e.getMessage());
        if (recoveryTimer != null) {
            recoveryTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    try {
                        jedis.ping();
                        // Bootstraps that ran during the outage only reached the
                        // (discarded) fallback, and pre-outage reads may feed
                        // post-outage writes. Drop the loaded sentinels so list
                        // caches re-bootstrap from the DB.
                        jedis.del(CACHE_LOADED_FLAGS.getBytes());
                        synchronized (RedisConnectionManager.this) {
                            log4j.debug("Redis recovered — resuming Redis cache (clearing in-memory fallback).");
                            fallback.clear();
                            redisAvailable = true;
                        }
                        cancel();
                    } catch (Exception ex) {
                        // still unavailable, keep probing
                    }
                }
            }, RECOVERY_INTERVAL_MS, RECOVERY_INTERVAL_MS);
        }
    }

    private void fallbackPut(String namespace, String field, Object value) {
        fallback.computeIfAbsent(namespace, k -> new ConcurrentHashMap<>()).put(field, value);
    }

    @SuppressWarnings("unchecked")
    private <T> T fallbackGet(String namespace, String field) {
        Map<String, Object> map = fallback.get(namespace);
        return map == null ? null : (T) map.get(field);
    }

    private void fallbackRemove(String namespace, String field) {
        Map<String, Object> map = fallback.get(namespace);
        if (map != null) map.remove(field);
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
