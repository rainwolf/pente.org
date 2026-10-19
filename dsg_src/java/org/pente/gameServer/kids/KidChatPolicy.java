package org.pente.gameServer.kids;

/** Whether two players may exchange chat on persisted surfaces. */
public interface KidChatPolicy {

    boolean allowed(long pidA, long pidB);
}
