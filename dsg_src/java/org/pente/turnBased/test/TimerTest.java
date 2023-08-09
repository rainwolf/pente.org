package org.pente.turnBased.test;

import org.pente.gameServer.client.*;

public class TimerTest {

    public TimerTest() {
        super();

    }


    public static void main(String[] args) {

//		final MilliSecondGameTimer timer = new MilliSecondGameTimer("test");
//		timer.setStartMinutes(0);
//		timer.setStartSeconds(5);
//		timer.reset();
//		timer.addGameTimerListener(new GameTimerListener() {
//			boolean zeroLastTime=false;
//			int zeroCount=0;
//			public void timeChanged(int newSeconds, int newMinutes) {
//				System.out.println(timer.getState());
//				if (newMinutes == 0 && newSeconds == 0) {
//					if (zeroLastTime) zeroCount++;
//					zeroLastTime=true;
//					timer.stop();
//					System.out.println("done " + timer.getState()+", zerocount="+zeroCount);
//					timer.reset();
//					timer.go();
//				}
//				else {
//					zeroLastTime=false;
//					zeroCount=0;
//				}
//			}
//		});
//		
//		timer.go();

        final MilliSecondGameTimer timer = new MilliSecondGameTimer("test");
        timer.setStartMinutes(20);
        //timer.setStartSeconds(0);
        //timer.reset();
        timer.addGameTimerListener(new GameTimerListener() {

            public void timeChanged(int newSeconds, int newMinutes) {
                System.out.println("change " + timer.getState());
                if (newSeconds <= 0 && newMinutes <= 0) {
                    System.out.println("doh");
                    System.exit(0);
                }
            }
        });

        long start = System.currentTimeMillis();
        for (int j = 0; j < 1000; j++) {
            System.out.println("before reset " + timer.getState());
            timer.reset();
            for (int i = 0; i < 10000; i++) {
                System.out.println("before go " + timer.getState());
                timer.go();
                System.out.println("before stop " + timer.getState());
                timer.stop();
            }
        }
    }

}
