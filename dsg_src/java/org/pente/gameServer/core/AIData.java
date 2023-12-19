/**
 * AIData.java
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

package org.pente.gameServer.core;

import java.util.*;

public class AIData implements java.io.Serializable, Cloneable {

    private String name;
    private String className;
    private int numLevels;
    private int[] validGames;
    private Hashtable<String, String> options;

    private int level;
    private int seat;
    private int game;

    public AIData() {
        validGames = new int[3];
        for (int i = 0; i < validGames.length; i++) {
            validGames[i] = -1;
        }

        options = new Hashtable<>();
    }

    public AIData(String name, int level, int seat, int game) {
        this();

        setName(name);
        setLevel(level);
        setSeat(seat);
        setGame(game);
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public int getNumLevels() {
        return numLevels;
    }

    public void setNumLevels(int numLevels) {
        this.numLevels = numLevels;
    }

    public void addValidGame(int game) {
        for (int i = 0; i < validGames.length; i++) {
            if (validGames[i] == -1) {
                validGames[i] = game;
                break;
            }
        }
    }

    public boolean isValidForGame(int game) {
        for (int i = 0; i < validGames.length; i++) {
            if (validGames[i] == game) {
                return true;
            }
        }
        return false;
    }

    public void setOption(String name, String value) {
        options.put(name, value);
    }

    public String getOption(String name) {
        return options.get(name);
    }

    public Enumeration getOptionNames() {
        return options.keys();
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public void setSeat(int seat) {
        this.seat = seat;
    }

    public int getSeat() {
        return seat;
    }

    public int getGame() {
        return game;
    }

    public void setGame(int game) {
        this.game = game;
    }

    public String getUserIDName() {
        return getName() + getLevel();
    }

    public boolean isValid() {

        if (level < 1 || level > numLevels) {
            return false;
        }
        if (!isValidForGame(game)) {
            return false;
        }

        return true;
    }

    public String toString() {
        return "[Name=" + getName() + ", Level=" + getLevel() + "," +
                " Seat=" + getSeat() + "]";
    }

    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException c) {
        }
        return null;
    }
}
