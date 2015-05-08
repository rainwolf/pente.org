/** CoordinatesListComponent.java
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

package org.pente.gameServer.client;

import java.awt.Color;

import org.pente.gameServer.core.*;

public interface CoordinatesListComponent {
 	public void setGame(int game);
	public void setPlayer(int playerNum, String playerName);
    public void removePlayer(String playerName);
    
    public void setHighlightColor(Color color);

    public void addOrderedPieceCollectionVisitListener(OrderedPieceCollection collection);
    public void removeOrderedPieceCollectionVisitListener(OrderedPieceCollection collection);
}