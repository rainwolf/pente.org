package org.pente.gameServer.core;

import org.pente.gameServer.event.DSGEvent;
import org.pente.gameServer.event.DSGEventListener;

/**
 * Owns the serialization invariant for one server room or table: a single
 * {@link SynchronizedQueue} drained by one dedicated thread that hands each
 * event to a {@link DSGEventListener} sink. Every event submitted to a pump is
 * processed on that one thread, in submission order.
 *
 * Replaces the hand-copied queue + thread + running-flag lifecycle that was
 * duplicated in SynchronizedServerTable and SynchronizedServerMainRoom.
 */
public final class SerialEventPump {

    private final SynchronizedQueue queue = new SynchronizedQueue();
    private final Thread thread;
    private volatile boolean running = true;

    public SerialEventPump(String threadName, DSGEventListener sink) {
        thread = new Thread(() -> {
            while (running) {
                try {
                    sink.eventOccurred((DSGEvent) queue.remove());
                } catch (InterruptedException e) {
                }
            }
        }, threadName);
        thread.start();
    }

    public void submit(DSGEvent event) {
        queue.add(event);
    }

    public void stop() {
        running = false;
        thread.interrupt();
    }

    /** Read-only observability for tests; never mutates lifecycle. */
    public boolean isAlive() {
        return thread.isAlive();
    }
}
