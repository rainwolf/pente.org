/** SimpleGridPiece.java
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

import java.awt.Color;

public class SimpleGridPiece implements GridPiece {
	
	private static int idSeq = 2;
	
	private int id;
	
    private int x;
    private int y;
    private int player;
    private Color color = null;
    
    private int depth;
    
    public SimpleGridPiece() {
    }
    public SimpleGridPiece(int x, int y, int player) {
        this.x = x;
        this.y = y;
        this.player = player;
        
        this.id = idSeq++;
    }

    public int getId() {
    	return id;
    }
    
    public int getX() {
        return x;
    }
    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }
    public void setY(int y) {
        this.y = y;
    }

    public Color getColor() {
    	return color;
    }
    public void setColor(Color c) {
    	this.color = c;
    }
    
    public int getPlayer() {
        return player;
    }
    public void setPlayer(int player) {
        this.player = player;
    }

    public int getDepth() {
    	return depth;
    }
    public void setDepth(int depth) {
    	this.depth = depth;
    }
    
    public boolean equals(Object o) {
        if (!(o instanceof GridPiece)) {
            return false;
        }
        GridPiece p = (GridPiece) o;
        
        return p.getPlayer() == getPlayer() &&
               p.getX() == getX() &&
               p.getY() == getY();
    }

    public String toString() {
        return "coords = [" + x + ", " + y + "], player = " + player;
    }
}