package org.pente.gameServer.kids;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Category;

import org.pente.gameServer.server.RedisConnectionManager;

import redis.clients.jedis.UnifiedJedis;

/** {@link ChatCopyStore} on Redis lists. Does nothing when Redis is down. */
public class RedisChatCopyStore implements ChatCopyStore {

    private static Category log4j = Category.getInstance(RedisChatCopyStore.class.getName());

    /** Null when Redis is not usable. */
    private UnifiedJedis jedis() {
        RedisConnectionManager m = RedisConnectionManager.getInstance();
        UnifiedJedis j = m.getJedis();
        if (j == null || !m.isAvailable()) {
            return null;
        }
        return j;
    }

    public void append(String key, String line, int ttlSeconds) {
        try {
            UnifiedJedis j = jedis();
            if (j == null) {
                return;
            }
            j.rpush(key, line);
            j.expire(key, ttlSeconds);
        } catch (Exception e) {
            log4j.error("Kid live chat buffer append failed: " + key, e);
        }
    }

    public List<String> drain(String key) {
        try {
            UnifiedJedis j = jedis();
            if (j == null) {
                return new ArrayList<String>();
            }
            List<String> lines = j.lrange(key, 0, -1);
            j.del(key);
            return lines == null ? new ArrayList<String>() : lines;
        } catch (Exception e) {
            log4j.error("Kid live chat buffer drain failed: " + key, e);
            return new ArrayList<String>();
        }
    }

    public void restore(String key, List<String> lines, int ttlSeconds) {
        try {
            if (lines == null || lines.isEmpty()) {
                return;
            }
            UnifiedJedis j = jedis();
            if (j == null) {
                return;
            }
            String[] reversed = new String[lines.size()];
            for (int i = 0; i < reversed.length; i++) {
                reversed[i] = lines.get(lines.size() - 1 - i);
            }
            j.lpush(key, reversed);
            j.expire(key, ttlSeconds);
        } catch (Exception e) {
            log4j.error("Kid live chat buffer restore failed: " + key, e);
        }
    }
}
