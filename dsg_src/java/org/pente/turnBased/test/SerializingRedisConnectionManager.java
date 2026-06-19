package org.pente.turnBased.test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.pente.gameServer.server.RedisConnectionManager;

/**
 * Test-only RedisConnectionManager that faithfully mimics real Redis semantics
 * by serializing values to byte[] on put and deserializing (producing an
 * INDEPENDENT copy) on get.
 *
 * The production in-memory {@code fallback} map stores raw object references, so
 * a get returns the same instance that was put — meaning a cached game and a
 * cached set never diverge even when a mutator forgets to persist. That makes
 * divergence tests toothless. This subclass serializes/deserializes through the
 * same {@link RedisConnectionManager#serialize}/{@link RedisConnectionManager#deserialize}
 * helpers real Redis would use, so reads return copies and divergence is detectable.
 *
 * The long/int hput/hget/hexists/hremove overloads in the parent all delegate to
 * the String-keyed methods overridden here, so overriding the String-keyed
 * methods (plus invalidate/hgetAllValues/hgetAllFields) covers every code path.
 */
public class SerializingRedisConnectionManager extends RedisConnectionManager {

    private final Map<String, Map<String, byte[]>> store = new ConcurrentHashMap<String, Map<String, byte[]>>();

    // When false, mimics a Redis outage: writes are dropped and reads return
    // null/empty (exactly how the production manager behaves when redisAvailable
    // is false), so callers must fall back to the DB.
    private volatile boolean available = true;

    public SerializingRedisConnectionManager() {
        super();
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public <T extends Serializable> void hput(String namespace, String field, T value) {
        if (!available || value == null) return;
        store.computeIfAbsent(namespace, k -> new ConcurrentHashMap<String, byte[]>())
             .put(String.valueOf(field), RedisConnectionManager.serialize(value));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T hget(String namespace, String field) {
        if (!available) return null;
        Map<String, byte[]> map = store.get(namespace);
        if (map == null) return null;
        byte[] bytes = map.get(String.valueOf(field));
        if (bytes == null) return null;
        return (T) RedisConnectionManager.deserialize(bytes);
    }

    @Override
    public boolean hexists(String namespace, String field) {
        if (!available) return false;
        Map<String, byte[]> map = store.get(namespace);
        return map != null && map.containsKey(String.valueOf(field));
    }

    @Override
    public void hremove(String namespace, String field) {
        if (!available) return;
        Map<String, byte[]> map = store.get(namespace);
        if (map != null) {
            map.remove(String.valueOf(field));
        }
    }

    @Override
    public void invalidate(String namespace) {
        if (!available) return;
        store.remove(namespace);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> hgetAllValues(String namespace) {
        if (!available) return new ArrayList<T>();
        Map<String, byte[]> map = store.get(namespace);
        if (map == null) return new ArrayList<T>();
        List<T> result = new ArrayList<T>(map.size());
        for (Map.Entry<String, byte[]> e : map.entrySet()) {
            if (RedisConnectionManager.LOADED_FIELD.equals(e.getKey())) continue; // skip the loaded marker
            result.add((T) RedisConnectionManager.deserialize(e.getValue()));
        }
        return result;
    }

    @Override
    public List<String> hgetAllFields(String namespace) {
        if (!available) return new ArrayList<String>();
        Map<String, byte[]> map = store.get(namespace);
        if (map == null) return new ArrayList<String>();
        List<String> fields = new ArrayList<String>(map.keySet());
        fields.remove(RedisConnectionManager.LOADED_FIELD); // never expose the loaded marker as a data field
        return fields;
    }

    /**
     * Simulates total Redis data loss while the JVM keeps running (container
     * OOM-kill without an RDB save, FLUSHALL, failover to a cleared fallback).
     */
    public void flushAll() {
        store.clear();
    }
}
