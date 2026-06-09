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

    public SerializingRedisConnectionManager() {
        super();
    }

    @Override
    public <T extends Serializable> void hput(String namespace, String field, T value) {
        if (value == null) return;
        store.computeIfAbsent(namespace, k -> new ConcurrentHashMap<String, byte[]>())
             .put(String.valueOf(field), RedisConnectionManager.serialize(value));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T hget(String namespace, String field) {
        Map<String, byte[]> map = store.get(namespace);
        if (map == null) return null;
        byte[] bytes = map.get(String.valueOf(field));
        if (bytes == null) return null;
        return (T) RedisConnectionManager.deserialize(bytes);
    }

    @Override
    public boolean hexists(String namespace, String field) {
        Map<String, byte[]> map = store.get(namespace);
        return map != null && map.containsKey(String.valueOf(field));
    }

    @Override
    public void hremove(String namespace, String field) {
        Map<String, byte[]> map = store.get(namespace);
        if (map != null) {
            map.remove(String.valueOf(field));
        }
    }

    @Override
    public void invalidate(String namespace) {
        store.remove(namespace);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> hgetAllValues(String namespace) {
        Map<String, byte[]> map = store.get(namespace);
        if (map == null) return new ArrayList<T>();
        List<T> result = new ArrayList<T>(map.size());
        for (byte[] bytes : map.values()) {
            result.add((T) RedisConnectionManager.deserialize(bytes));
        }
        return result;
    }

    @Override
    public List<String> hgetAllFields(String namespace) {
        Map<String, byte[]> map = store.get(namespace);
        if (map == null) return new ArrayList<String>();
        return new ArrayList<String>(map.keySet());
    }
}
