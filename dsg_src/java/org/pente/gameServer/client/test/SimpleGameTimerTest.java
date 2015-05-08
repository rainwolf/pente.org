package org.pente.gameServer.client.test;

import java.awt.*;
import java.awt.event.*;

import org.pente.gameServer.client.*;

public class SimpleGameTimerTest {

    public static void main(String[] args) {
 
		final Frame f = new Frame("SimpleGameTimer");
		
		//final GameTimer gameTimer = new SimpleGameTimer();
		final GameTimer gameTimer = new MilliSecondGameTimer("Test");
		gameTimer.setStartMinutes(2);
		gameTimer.setStartSeconds(0);
		gameTimer.reset();

		final Label timerLabel = new Label("2:00");


		f.add(timerLabel);
		
		f.pack();
		f.setLocation(100, 100);
		f.setVisible(true);
		
		f.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				f.dispose();
				gameTimer.destroy();
			}
		});

		final long start = System.currentTimeMillis();
		gameTimer.go();
		

        gameTimer.addGameTimerListener(new GameTimerListener() {
            public void timeChanged(int newMinutes, int newSeconds) {
            	
            	if (newMinutes == 0 && newSeconds == 0) {
            		System.out.println("total time elapsed = " + (System.currentTimeMillis() - start));
            		gameTimer.stop();
            	}
            	
                String newSecondsStr = newSeconds > 9 ? "" + newSeconds : "0" + newSeconds;
                timerLabel.setText(newMinutes + ":" + newSecondsStr);
                
                // dummy loop to slow down the timer
                //for (int i = 0; i < 500; i++) {
                //	System.out.print(i);
                //}
                //System.out.println();
            }
        });
    }
}

