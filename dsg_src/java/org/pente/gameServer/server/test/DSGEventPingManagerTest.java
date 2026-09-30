package org.pente.gameServer.server.test;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.pente.gameServer.event.DSGEvent;
import org.pente.gameServer.event.DSGEventListener;
import org.pente.gameServer.event.DSGPingEvent;
import org.pente.gameServer.server.DSGEventPingManager;
import org.pente.gameServer.server.DSGEventToPlayerRouter;

/**
 * A player removed after the ping thread snapshots its player map, but
 * before that player's ping is sent, must not kill the ping thread.
 */
public class DSGEventPingManagerTest extends TestCase {

    private static final String THREAD_NAME = "DSGEventPingManager";
    private static final long TIMEOUT_MS = 5000;

    private DSGEventPingManager pingManager;

    public DSGEventPingManagerTest(String name) {
        super(name);
    }

    /** On its first ping, removes the other player from the ping manager. */
    private static class RemovingRouter implements DSGEventToPlayerRouter {
        final List<String> routed = new ArrayList<>();
        DSGEventPingManager pingManager;

        public void addRoute(DSGEventListener l, String name) {
        }

        public DSGEventListener removeRoute(String name) {
            return null;
        }

        public DSGEventListener getRoute(String name) {
            return null;
        }

        public void routeEvent(DSGEvent dsgEvent, String name) {
            assertEquals(name, ((DSGPingEvent) dsgEvent).getPlayer());
            synchronized (routed) {
                if (routed.isEmpty()) {
                    pingManager.removePlayer(name.equals("a") ? "b" : "a");
                }
                routed.add(name);
                routed.notifyAll();
            }
        }
    }

    protected void tearDown() {
        if (pingManager != null) {
            pingManager.destroy();
        }
    }

    public void testRemovalBetweenSnapshotAndSendKeepsThreadAlive() throws Exception {
        RemovingRouter router = new RemovingRouter();
        pingManager = new DSGEventPingManager(router);
        router.pingManager = pingManager;

        // wait for the (empty) first round to finish, so the next round's
        // snapshot is guaranteed to hold both players
        Thread pingThread = findPingThread();
        waitForState(pingThread, Thread.State.TIMED_WAITING);

        pingManager.addPlayer("a");
        pingManager.addPlayer("b");
        pingThread.interrupt(); // wake from sleep: run a round now

        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        synchronized (router.routed) {
            while (router.routed.size() < 2 && System.currentTimeMillis() < deadline) {
                router.routed.wait(100);
            }
            assertEquals("both snapshot players pinged", 2, router.routed.size());
        }

        // back asleep for the next round, not dead
        waitForState(pingThread, Thread.State.TIMED_WAITING);
        assertTrue(pingThread.isAlive());
    }

    private static Thread findPingThread() {
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (THREAD_NAME.equals(t.getName()) && t.isAlive()) {
                return t;
            }
        }
        throw new AssertionError("ping thread not running");
    }

    private static void waitForState(Thread t, Thread.State state) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (t.getState() != state) {
            if (!t.isAlive() || System.currentTimeMillis() > deadline) {
                fail("ping thread " + t.getState() + ", expected " + state);
            }
            Thread.sleep(10);
        }
    }
}
