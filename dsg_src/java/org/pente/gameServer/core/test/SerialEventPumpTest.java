package org.pente.gameServer.core.test;

import java.util.*;
import java.util.concurrent.*;

import junit.framework.TestCase;

import org.pente.gameServer.core.SerialEventPump;
import org.pente.gameServer.event.AbstractDSGEvent;
import org.pente.gameServer.event.DSGEvent;
import org.pente.gameServer.event.DSGEventListener;

/**
 * Unit tests for {@link SerialEventPump}. Needs no sockets, DB, or Resources —
 * the whole point of extracting the pump is that its serialization contract is
 * verifiable in isolation.
 */
public class SerialEventPumpTest extends TestCase {

    public SerialEventPumpTest(String name) {
        super(name);
    }

    /** Minimal DSGEvent carrying a sequence number. */
    private static final class SeqEvent extends AbstractDSGEvent {
        final int seq;
        SeqEvent(int seq) { this.seq = seq; }
    }

    /** Submitted events are processed exactly once, in FIFO order, on one named thread. */
    public void testEventsProcessedFifoOnSingleNamedThread() throws Exception {
        final int n = 200;
        final String threadName = "pump-under-test";
        final List<Integer> order = Collections.synchronizedList(new ArrayList<Integer>());
        final Set<String> threads = Collections.synchronizedSet(new HashSet<String>());
        final CountDownLatch done = new CountDownLatch(n);

        DSGEventListener sink = new DSGEventListener() {
            public void eventOccurred(DSGEvent e) {
                order.add(((SeqEvent) e).seq);
                threads.add(Thread.currentThread().getName());
                done.countDown();
            }
        };

        SerialEventPump pump = new SerialEventPump(threadName, sink);
        try {
            for (int i = 0; i < n; i++) {
                pump.submit(new SeqEvent(i));
            }
            assertTrue("pump did not process all events within timeout",
                    done.await(5, TimeUnit.SECONDS));
        } finally {
            pump.stop();
        }

        assertEquals("every submitted event processed exactly once", n, order.size());
        for (int i = 0; i < n; i++) {
            assertEquals("FIFO order preserved at index " + i,
                    Integer.valueOf(i), order.get(i));
        }
        assertEquals("all events ran on exactly one thread", 1, threads.size());
        assertTrue("events ran on the pump's named thread", threads.contains(threadName));
    }

    /** stop() must terminate the drain thread (deterministic, bounded poll — no fixed sleep). */
    public void testStopTerminatesDrainThread() throws Exception {
        final CountDownLatch firstProcessed = new CountDownLatch(1);
        DSGEventListener sink = new DSGEventListener() {
            public void eventOccurred(DSGEvent e) { firstProcessed.countDown(); }
        };

        SerialEventPump pump = new SerialEventPump("pump-stop-test", sink);
        // Prove the drain thread is up and has looped back to a blocked remove().
        pump.submit(new SeqEvent(1));
        assertTrue("pump never started draining", firstProcessed.await(5, TimeUnit.SECONDS));
        assertTrue("pump thread should be alive before stop()", pump.isAlive());

        pump.stop();

        long deadline = System.currentTimeMillis() + 2000;
        while (pump.isAlive() && System.currentTimeMillis() < deadline) {
            Thread.yield();
        }
        assertTrue("stop() must terminate the drain thread", !pump.isAlive());
    }
}
