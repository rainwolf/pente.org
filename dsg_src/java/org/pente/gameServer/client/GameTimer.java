/**
 * GameTimer.java
 * Copyright (C) 2001 Dweebo's Stone Games (http://www.pente.org/)
 * <p>
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, you can find it online at
 * http://www.gnu.org/copyleft/gpl.txt
 */

package org.pente.gameServer.client;

public interface GameTimer {

    public void addGameTimerListener(GameTimerListener listener);

    public void removeGameTimerListener(GameTimerListener listener);

    public void setStartMinutes(int minutes);

    public int getStartMinutes();

    public void setStartSeconds(int seconds);

    public int getStartSeconds();

    public void increment(int incrementSeconds);

    public void incrementMillis(int incrementMillis);

    public void adjust(int newMinutes, int newSeconds);

    public void adjust(int newMinutes, int newSeconds, int newMillis);

    public int getMinutes();

    public int getSeconds();

    public long getMillis();

    public void reset();

    public void go();

    public boolean isRunning();

    public void stop();

    public void destroy();
}