/**
 * GridPiece.java
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

import java.awt.Color;

public interface GridPiece {

    public int getX();

    public void setX(int x);

    public int getY();

    public void setY(int y);

    public int getPlayer();

    public void setPlayer(int player);

    public Color getColor();

    public void setColor(Color c);

    public int getDepth();

    public void setDepth(int depth);
}