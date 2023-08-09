/**
 * TraditionalGridCoordinates.java
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

import java.awt.Point;

public class TraditionalGridCoordinates implements GridCoordinates {

    private String coordsX[];
    private String coordsY[];

    private int gridSize = 19;


    public TraditionalGridCoordinates(int gridWidth, int gridHeight) {
        coordsX = new String[gridWidth];
        coordsY = new String[gridHeight];

        int maxX = gridWidth / 2;
        int maxY = gridHeight / 2;

        int j = 0;
        for (int i = maxX; i >= 0; i--, j++) {
            coordsX[j] = Integer.toString(i);
        }
        for (int i = 1; i <= maxX; i++, j++) {
            coordsX[j] = Integer.toString(i);
        }

        j = 0;
        for (int i = maxY; i >= 0; i--, j++) {
            coordsY[j] = Integer.toString(i);
        }
        for (int i = 1; i <= maxY; i++, j++) {
            coordsY[j] = Integer.toString(i);
        }
    }

    public String[] getXCoordinates() {
        return coordsX;
    }

    public String[] getYCoordinates() {
        return coordsY;
    }

    public String getCoordinate(int move) {
        int x = move % gridSize;
        int y = gridSize - 1 - move / gridSize;

        return getCoordinate(x, y);
    }

    public String getCoordinate(int x, int y) {

        String move = "";
        int middleX = gridSize / 2;
        int middleY = gridSize / 2;

        if (x == middleX && y == middleY) {
            move = "0";
        } else {

            if (x > middleX) {
                x = x - middleX;
                move = "R" + Integer.toString(x);
            } else if (x < middleX) {
                x = middleX - x;
                move = "L" + Integer.toString(x);
            }

            if (y > middleY) {
                y = y - middleY;
                move += "U" + Integer.toString(y);
            } else if (y < middleY) {
                y = middleY - y;
                move += "D" + Integer.toString(y);
            }
        }

        return move;
    }

    public Point getPoint(String coordinate) {
        /**@todo: Implement this org.pente.gameServer.core.GridCoordinates method*/
        //throw new java.lang.UnsupportedOperationException("Method getPoint() not yet implemented.");
        return new Point(0, 0);
    }

    @Override
    public void setGridSize(int gridSize) {
        this.gridSize = gridSize;
    }
}