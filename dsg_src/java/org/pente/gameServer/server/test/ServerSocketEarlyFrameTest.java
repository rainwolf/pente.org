package org.pente.gameServer.server.test;

import junit.framework.TestCase;
import org.pente.gameServer.event.*;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.*;

/**
 * A client sends its login frame as soon as the connection opens. The frame
 * must reach the listener that Server.addPlayerSocket registers, so the
 * handler may not start reading before go() is called.
 */
public class ServerSocketEarlyFrameTest extends TestCase {

    public ServerSocketEarlyFrameTest(String name) {
        super(name);
    }

    public void testFrameSentOnConnectReachesListener() throws Exception {
        byte[] json = new DSGEventWrapper(new DSGLoginEvent(true, new ClientInfo()))
                .getJSON().getBytes("UTF-8");
        byte[] frame = new byte[json.length + 1];
        System.arraycopy(json, 0, frame, 0, json.length);
        frame[json.length] = (byte) 255;

        FrameSocket socket = new FrameSocket(frame);
        ServerSocketDSGEventHandler handler = new ServerSocketDSGEventHandler(socket);
        try {
            // same order as Server.addPlayerSocket: construct, add listener, go.
            // Give a reader that was started too early time to eat the frame.
            socket.in.frameConsumed.await(2, TimeUnit.SECONDS);

            BlockingQueue<DSGEvent> received = new LinkedBlockingQueue<>();
            handler.addListener(received::add);
            handler.go();

            DSGEvent event = received.poll(5, TimeUnit.SECONDS);
            assertNotNull("login frame sent on connect never reached the listener", event);
            assertTrue("expected the login event, got " + event, event instanceof DSGLoginEvent);
            assertTrue(((DSGLoginEvent) event).isGuest());
        } finally {
            handler.destroy();
        }
    }

    /** Serves one frame immediately, then blocks until closed. */
    private static class FrameInputStream extends InputStream {
        private final byte[] frame;
        private int pos = 0;
        final CountDownLatch frameConsumed = new CountDownLatch(1);
        final CountDownLatch closed = new CountDownLatch(1);

        FrameInputStream(byte[] frame) {
            this.frame = frame;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n == -1 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            synchronized (this) {
                if (pos < frame.length) {
                    int n = Math.min(len, frame.length - pos);
                    System.arraycopy(frame, pos, b, off, n);
                    pos += n;
                    return n;
                }
            }
            frameConsumed.countDown();
            try {
                closed.await();
            } catch (InterruptedException e) {
                throw new InterruptedIOException();
            }
            return -1;
        }

        @Override
        public void close() {
            closed.countDown();
        }
    }

    private static class FrameSocket extends Socket {
        final FrameInputStream in;

        FrameSocket(byte[] frame) {
            this.in = new FrameInputStream(frame);
        }

        @Override
        public InputStream getInputStream() {
            return in;
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public synchronized void close() throws IOException {
            in.close();
            super.close();
        }
    }
}
