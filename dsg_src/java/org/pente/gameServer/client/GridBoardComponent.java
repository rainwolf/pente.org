/**
 * GridBoardComponent.java
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

import java.awt.*;
import java.util.List;
import java.util.Map;

import org.pente.gameServer.core.*;

public interface GridBoardComponent extends PieceCollection {

    public int getGridWidth();

    public void setGridWidth(int width);

    public int getGridHeight();

    public void setGridHeight(int height);

    public boolean getOnGrid();

    public void setOnGrid(boolean onGrid);

    public void setBackgroundColor(int color);

    public void setGridColor(int color);

    public void setHighlightColor(int color);

    public void setGameNameColor(int color);

    public void setGameName(String name);

    public void setTerritory(Map<Integer, List<Integer>> territory);

    public void setHighlightPiece(GridPiece gridPiece);

    public void setThinkingPieceVisible(boolean visible);

    public void setThinkingPiecePlayer(int player);

    public void setNewMovesAvailable(boolean available);

    public void setDrawInnerCircles(boolean drawInnerCircles);

    public void setDrawGoDots(boolean drawGoDots);

    public void setDrawCoordinates(boolean drawCoordinates);

    public void setBoardInsets(int l, int t, int r, int b);

    public void setMessage(String message);

    public void addGridBoardListener(GridBoardListener listener);

    public void removeGridBoardListener(GridBoardListener listener);

    public void setCursor(int cursor);

    public void refresh();

    public void destroy();
}