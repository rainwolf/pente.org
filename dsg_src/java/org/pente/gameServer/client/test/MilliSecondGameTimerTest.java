package org.pente.gameServer.client.test;

import junit.framework.*;

import org.pente.gameServer.client.*;

public class MilliSecondGameTimerTest extends TestCase {


    public static void main(String[] args) {
        //junit.swingui.TestRunner.main(new String[] {
        //    SimpleGridStateTest.class.getName()
        //});
        //junit.awtui.TestRunner.main(new String[] {
        //    SimpleGridStateTest.class.getName()
        //});
        junit.textui.TestRunner.main(new String[]{
                MilliSecondGameTimerTest.class.getName()
        });
    }

    private MilliSecondGameTimer gameTimer;
    long startTime;

    public MilliSecondGameTimerTest(String name) {
        super(name);
    }

    public void setUp() {
        gameTimer = new MilliSecondGameTimer("Test");
        startTime = System.currentTimeMillis();
    }

    public void tearDown() {
        gameTimer.destroy();
    }

    private void checkDiff(long timeExpected) {

        long endTime = System.currentTimeMillis();

        long diff = timeExpected - (endTime - startTime);
        if (diff > 100 || diff < -100) {
            fail("Difference is too great " + diff);
        }
    }

    public void testOneMinute() {

        final Object lock = new Object();

        gameTimer.setStartSeconds(60);
        gameTimer.reset();
        gameTimer.addGameTimerListener(new GameTimerListener() {
            public void timeChanged(int m, int s) {
                long t = System.currentTimeMillis();
                long tt = t - startTime;
                long expected = (60 - s) * 1000;
                long diff = tt - expected;
                System.out.println("m=" + m + ",s=" + s + ",tt=" + tt + ",diff=" + diff);
                if (m == 0 && s == 0) {
                    synchronized (lock) {
                        lock.notify();
                    }
                }
            }
        });


        gameTimer.go();
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }

        checkDiff(60000);
    }

    public void testSeconds60Problem() {

        gameTimer.setStartMinutes(0);
        gameTimer.setStartSeconds(0);
        gameTimer.reset();
        gameTimer.increment(59);

        assertEquals(0, gameTimer.getMinutes());
        assertEquals(59, gameTimer.getSeconds());

        gameTimer.incrementMillis(300);

        assertEquals(1, gameTimer.getMinutes());
        assertEquals(0, gameTimer.getSeconds());

        gameTimer.incrementMillis(800);

        assertEquals(1, gameTimer.getMinutes());
        assertEquals(1, gameTimer.getSeconds());
    }

    public void testSimpleRun() {

        gameTimer.setStartMinutes(2);
        gameTimer.setStartSeconds(1);
        gameTimer.reset();

        gameTimer.go();
        sleep(65100);

        assertEquals(0, gameTimer.getMinutes());
        assertEquals(56, gameTimer.getSeconds());

    }

    public void testReset() {

        gameTimer.setStartSeconds(10);
        gameTimer.reset();
        for (int i = 0; i < 8; i++) {
            gameTimer.go();
            sleep(100);
            gameTimer.stop();
            sleep(500);
        }
        assertEquals("Reset time test 1", 10, gameTimer.getSeconds());

        gameTimer.reset();
        for (int i = 0; i < 8; i++) {
            gameTimer.go();
            sleep(100);
            gameTimer.stop();
            sleep(500);
        }
        assertEquals("Reset time test 2", 10, gameTimer.getSeconds());
    }

    public void testAdjust() {

        gameTimer.setStartSeconds(10);
        gameTimer.reset();
        for (int i = 0; i < 16; i++) {
            gameTimer.go();
            sleep(100);
            gameTimer.stop();
            sleep(500);
        }
        assertEquals("Adjust time test 1", 9, gameTimer.getSeconds());

        gameTimer.adjust(0, 20);
        for (int i = 0; i < 8; i++) {
            gameTimer.go();
            sleep(100);
            gameTimer.stop();
            sleep(500);
        }
        assertEquals("Adjust time test 2", 20, gameTimer.getSeconds());
    }

    public void testIncrement() {

        gameTimer.setStartSeconds(10);
        gameTimer.reset();
        System.out.println(gameTimer.getState());
        for (int i = 0; i < 8; i++) {
            gameTimer.go();
            sleep(100);
            gameTimer.stop();
            sleep(500);
        }
        System.out.println(gameTimer.getState());
        assertEquals("Increment time test 1", 10, gameTimer.getSeconds());
        gameTimer.increment(10);
        for (int i = 0; i < 8; i++) {
            gameTimer.go();
            sleep(100);
            gameTimer.stop();
            sleep(500);
        }
        System.out.println(gameTimer.getState());
        assertEquals("Increment time test 2", 19, gameTimer.getSeconds());
    }


    public void testStartUp() {

        gameTimer.setStartSeconds(10);
        gameTimer.reset();

        for (int i = 0; i < 8; i++) {
            gameTimer.go();
            sleep(100);
            gameTimer.stop();
            sleep(500);
        }

        assertEquals("Start time test", 10, gameTimer.getSeconds());
    }

    public void testTimerSleepLoopAlmostSecond() {
        timerSleepLoop(60, 800);
    }

    public void testTimerSleepLoopShort() {
        timerSleepLoop(60, 100);
    }

    private void timerSleepLoop(int repetition, int sleepTime) {
        gameTimer.setStartSeconds(repetition * sleepTime * 2 / 1000);
        gameTimer.reset();

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < repetition; i++) {
            gameTimer.go();
            sleep(sleepTime);
            gameTimer.stop();
            sleep(500);
        }

        long totalTime = System.currentTimeMillis() - startTime - (repetition * 500);

        int minutes = (int) (totalTime / 60000);
        int seconds = (int) (totalTime / 1000);
        //int minutes = repetition * sleepTime / 60000;
        //int seconds = repetition * sleepTime % 60000 / 1000;

        assertEquals("Minutes check", minutes, gameTimer.getMinutes());
        assertEquals("Seconds check", seconds, gameTimer.getSeconds());
    }


    public void testSlowListeners() {

        gameTimer.setStartSeconds(60);
        gameTimer.reset();
        gameTimer.addGameTimerListener(new GameTimerListener() {
            public void timeChanged(int m, int s) {
                long t = System.currentTimeMillis();
                long tt = t - startTime;
                long expected = (60 - s) * 1000;
                long diff = tt - expected;
                System.out.println("m=" + m + ",s=" + s + ",tt=" + tt + ",diff=" + diff);
                sleep(100);
            }
        });

        gameTimer.go();
        sleep(30100);
        gameTimer.stop();

        assertEquals("Time check", 30, gameTimer.getSeconds());
    }

    private void sleep(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
        }
    }
}

