package org.pente.turnBased.test;

import java.io.*;

public final class RedisConnectionManagerCloneHelper {
    public static Object clone(Serializable o) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            new ObjectOutputStream(b).writeObject(o);
            return new ObjectInputStream(new ByteArrayInputStream(b.toByteArray())).readObject();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
