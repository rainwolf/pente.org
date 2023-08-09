package org.pente.message;

import org.apache.log4j.Category;

import org.pente.gameServer.core.SynchronizedQueue;

public abstract class BackgroundWorker {

    private static Category log4j =
            Category.getInstance(BackgroundWorker.class.getName());

    private SynchronizedQueue synchronizedQueue;

    private Thread queueThread;
    private volatile boolean running;

    public BackgroundWorker(final String name) {

        synchronizedQueue = new SynchronizedQueue();

        Runnable queueRunnable = new Runnable() {
            public void run() {
                while (running) {
                    try {
                        internalDoWork(synchronizedQueue.remove());
                    } catch (InterruptedException e) {
                    } catch (Throwable t) {
                        log4j.error("Uncaught error for BackgroundWorker-" +
                                name, t);
                    }
                }
            }
        };

        running = true;
        queueThread = new Thread(queueRunnable,
                "BackgroundWorker-" + name);
        queueThread.start();

    }

    public void destroy() {
        running = false;
        if (queueThread != null) {
            queueThread.interrupt();
            queueThread = null;
        }
    }

    public void doWork(Object obj) {
        synchronizedQueue.add(obj);
    }

    public abstract void internalDoWork(Object obj);
}
