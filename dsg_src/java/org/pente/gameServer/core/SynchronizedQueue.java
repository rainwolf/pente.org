package org.pente.gameServer.core;

import java.util.*;

public class SynchronizedQueue {

    private Vector<Object> queue = new Vector<>();

    public synchronized void add(Object obj) {

        queue.addElement(obj);

        notifyAll();
    }

    public synchronized Object remove() throws InterruptedException {

        while (queue.isEmpty()) {
            wait();
        }

        Object o = queue.elementAt(0);
        queue.removeElementAt(0);
        return o;
    }

    public String toString() {
        return Integer.toString(queue.size());
    }
}

