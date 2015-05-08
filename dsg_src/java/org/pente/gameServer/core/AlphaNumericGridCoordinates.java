/** AlphaNumericGridCoordinates.java
 *  Copyright (C) 2001 Dweebo's Stone Games (http://www.pente.org/)
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, you can find it online at
 *  http://www.gnu.org/copyleft/gpl.txt
 */

package org.pente.gameServer.core;

import java.awt.Point;

public class AlphaNumericGridCoordinates implements GridCoordinates {

    private String letters[];
    private String numbers[];

    public AlphaNumericGridCoordinates(int gridWidth, int gridHeight) {

        numbers = new String[gridHeight];
        letters = new String[gridWidth];

        // setup numbers
        for (int i = 0; i < gridHeight; i++) {
            numbers[i] = Integer.toString(i + 1);
        }

        // setup letters
        char c[] = new char[1];
        for (int i = 0; i < gridWidth; i++) {
            c[0] = (char) (i + 65);
            if (i > 7) {
                c[0]++;
            }
            letters[i] = new String(c);
        }
    }

    public String[] getXCoordinates() {
        return letters;
    }
    public String[] getYCoordinates() {
        return numbers;
    }

	public String getCoordinate(int move) {
		int x = move % letters.length;
		int y = letters.length - 1 - move / letters.length;
		
		return getCoordinate(x, y);
	}
    public String getCoordinate(int x, int y) {
        return letters[x] + numbers[y];
    }

    public Point getPoint(String coordinate) {

        char letter = coordinate.charAt(0);
        int x = letter - 65;
        if (x > 7) {
            x--;
        }
        int y = (Integer.parseInt(coordinate.substring(1)) - 1);
        
        return new Point(x, y);
    }
}