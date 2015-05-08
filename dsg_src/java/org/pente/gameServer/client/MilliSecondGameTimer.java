package org.pente.gameServer.client;

import java.util.*;

public class MilliSecondGameTimer implements GameTimer {

	private int startMinutes;
	private int startSeconds;

	private long timeLeft;
	private long startTime;
	private long tempTimeElapsed;

    private Vector listeners;

    private Object timeLock;
    private Thread thread;
    private boolean running;
    private boolean alive;
    
    private String state;

	public MilliSecondGameTimer(String threadName) {
        alive = true;
        listeners = new Vector();
        timeLock = new Object();

        thread = new Thread(new LocalRunnable(), "MilliSecondGameTimer " + threadName);
        thread.start();
	}

	public boolean isRunning() {
		return running;
	}
	
    public void addGameTimerListener(GameTimerListener listener) {
    	listeners.addElement(listener);
    }
    public void removeGameTimerListener(GameTimerListener listener) {
    	listeners.removeElement(listener);
    }
    private void timeChanged(long localTimeLeft) {

        for (int i = 0; i < listeners.size(); i++) {
            GameTimerListener listener = (GameTimerListener) listeners.elementAt(i);
            listener.timeChanged(convertMillisToMinutes(localTimeLeft), 
            				     convertMillisToSeconds(localTimeLeft));
        }
    }

    public void setStartMinutes(int minutes) {
        this.startMinutes = minutes;
    }
    public int getStartMinutes() {
        return startMinutes;
    }

    public void setStartSeconds(int seconds) {
        this.startSeconds = seconds;
    }
    public int getStartSeconds() {
        return startSeconds;
    }

	private long getStartMillis() {
		return convertMinutesToMillis(startMinutes) +
			   convertSecondsToMillis(startSeconds);
	}

    public int getMinutes() {
    	synchronized (timeLock) {
	        return convertMillisToMinutes(timeLeft);
    	}
    }

    public int getSeconds() {
        synchronized (timeLock) {
        	return convertMillisToSeconds(timeLeft);
        }
    }

	private static int convertMillisToMinutes(long millis) {
        int seconds = (int) Math.ceil((double) millis / 1000);
		return seconds / 60;
	}
	private static int convertMillisToSeconds(long millis) {
		return (int) Math.ceil((double) millis % 60000 / 1000) % 60;
	}
	private static long convertMinutesToMillis(int minutes) {
		return minutes * 60000;
	}
	private static long convertSecondsToMillis(int seconds) {
		return seconds * 1000;
	}

	/** Only call this method when the timer is stopped */
	public void incrementMillis(int incrementMillis) {
		long localTimeLeft;
		synchronized (timeLock) {

			tempTimeElapsed -= incrementMillis;
        	startTime -= incrementMillis;
			timeLeft += incrementMillis;

			localTimeLeft = timeLeft;
		}
		timeChanged(localTimeLeft);
	}
	/** Only call this method when the timer is stopped */
    public void increment(int incrementSeconds) {
    	incrementMillis((int) convertSecondsToMillis(incrementSeconds));
    }

	/** Only call this method when the timer is stopped */
    public void adjust(int newMinutes, int newSeconds) {
    	adjust(newMinutes, newSeconds, 0);
    }
	/** Only call this method when the timer is stopped */
    public void adjust(int newMinutes, int newSeconds, int newMillis) {
		long localTimeLeft;
        synchronized (timeLock) {
        	long newTime = convertMinutesToMillis(newMinutes) +
            			   convertSecondsToMillis(newSeconds) +
            			   newMillis;
        	tempTimeElapsed = getStartMillis() - newTime;
        	startTime = System.currentTimeMillis() - tempTimeElapsed;
            timeLeft = newTime;

            localTimeLeft = timeLeft;
        }
        timeChanged(localTimeLeft);
    }


	/** Only call this method when the timer is stopped */
    public void reset() {
    	long localTimeLeft;
        synchronized (timeLock) {
        	tempTimeElapsed = 0;
        	timeLeft = convertMinutesToMillis(startMinutes) +
        			   convertSecondsToMillis(startSeconds);
            localTimeLeft = timeLeft;
        }
        timeChanged(localTimeLeft);
    }

    public void go() {

        synchronized (timeLock) {

			startTime = System.currentTimeMillis();

            if (thread == null) {
                thread = new Thread(new LocalRunnable(), "MilliSecondGameTimer");
                thread.start();
            }

            running = true;
            timeLock.notify();
        }
    }

    public void stop() {

        synchronized (timeLock) {
        	if (running) {
        		
        		tempTimeElapsed += System.currentTimeMillis() - startTime;
        		timeLeft = getStartMillis() - tempTimeElapsed;

	            running = false;
 	   	 	    if (thread != null) {
  	     	     	thread.interrupt();
   	     	    }
        	}
        }
    }

    public void destroy() {

        synchronized (timeLock) {
            alive = false;

            if (thread != null) {
                thread.interrupt();
                thread = null;
            }
            if (listeners != null) {
                listeners.clear();
            }
        }
    }
    
    private class LocalRunnable implements Runnable {
    	public void run() {

			long normalSleepTime = 1000;
			long nextSleepTime = normalSleepTime;

	        while (true) {

	            synchronized (timeLock) {
                    
                    if (!alive) {
                        return;
                    }

	                while (!running && alive) {
	                    try {
	                    	state = "not running, waiting";
	                        timeLock.wait();
	                    } catch (InterruptedException ex) {
	                    }
	                }

                    if (!alive) {
                        return;
                    }

	            	if (timeLeft < 0) {
	            		nextSleepTime = 0;
	            	}
	            	else {
						long actualTimeElapsed = System.currentTimeMillis() - startTime + tempTimeElapsed;
						long timeElapsed = getStartMillis() - timeLeft;
						long diff = actualTimeElapsed - timeElapsed;
						if (normalSleepTime > Math.abs(diff)) {
							nextSleepTime = normalSleepTime - diff;
						}
						else {
							nextSleepTime = diff - normalSleepTime;
						}

                        if (nextSleepTime < 0) {
							nextSleepTime = 0;
						}
	            	}
	            }

	            try {
	            	state = "running, sleeping " + nextSleepTime;
	                Thread.sleep(nextSleepTime);
	            } catch (InterruptedException ex) {
	            }
	
	            synchronized (timeLock) {

                    if (!alive) {
                        return;
                    } else if (!running) {
                        continue;
                    }

					timeLeft -= normalSleepTime;
	            }

                // stop the timer when time is less than 0
                if (timeLeft <= 0) {
                    synchronized (timeLock) {
                        running = false;
                        timeLeft = 0;
                    }
                }

                timeChanged(timeLeft);
	        }
    	}
    }
    
    public String getState() {
    	synchronized (timeLock) {
	    	String s = state + ", running=" + running + ", alive=" + alive +
	    		", timeLeft=" + timeLeft + ", tempTimeElapsed=" + tempTimeElapsed; 
	    	return s;
    	}
    }
}