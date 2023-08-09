/**
 * IYTMoveFilter.java
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

package org.pente.filter.iyt;

import java.awt.*;

import org.pente.filter.*;

/** Line filter that looks for a move in a game
 *  @see IYTMovesBuilder
 *  @since 0.1
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public class IYTMoveFilter implements LineFilter {

    /** The filtered move */
    private int move;

    /** Flag if filtering was successful or not */
    private boolean filterSuccess;

    /** Status flag */
    private boolean processingBoard;
    /** Status flag */
    private boolean searchingForBoard;

    /** The current coordinate being processed */
    private int x, y;

    /** The dimensions of the game board */
    private static Dimension boardSize;

    /** The start of board indicator */
    private final String START_BOARD = "Current state of this game";
    private final int START_BOARD_FIRST_OFFSET = -1;
    private final int ROW_LINE_OFFSET = -2;

    /** The previous filter to call before filtering */
    private LineFilter prevFilter;

    /** Use this constructor if this is the base filter in the chain */
    public IYTMoveFilter() {
        this(null);
    }

    /** Use this constructor if this is not the base filter in the chain
     *  @param prevFilter The previous filter to call before filtering
     */
    public IYTMoveFilter(LineFilter prevFilter) {
        this.prevFilter = prevFilter;

        searchingForBoard = true;
        boardSize = new Dimension(19, 19);
        move = -1;
    }

    /** Get the filtered move
     *  @return int The filtered move
     */
    public int getMove() {
        return move;
    }

    /** Tell if the move was filtered ok or not
     *  @return boolean Filtering success
     */
    public boolean wasMoveFiltered() {
        return filterSuccess;
    }

    /** Look for the move
     *  @param line The line to filter
     *  @return String The filtered line
     */
    public String filterLine(String line) {

        // if there is a previous filter, allow it to filter before
        // doing any other filtering.
        if (prevFilter != null) {
            line = prevFilter.filterLine(line);
        }

        // if the line is null or done filtering, don't filter
        if (line == null || filterSuccess) {
            return line;
        }

        int index = line.indexOf("var hiliteflag = \"");
        if (index != -1) {
            String line2 = line.substring(index + 18);
            move = line2.indexOf("1");
            filterSuccess = true;
        }

        return line;
    }
}