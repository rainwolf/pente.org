/**
 * DSG2_12GameFormat.java
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

package org.pente.game;

import java.text.*;
import java.util.*;

/** A game formatter used mainly to parse games stored in the format used
 *  at www.pente.org with version 2.12.  This doesn't get used much anymore
 *  since www.pente.org has been modified to directly send games to the
 *  database and uses the newer PGNGameFormat.
 *
 *  @author dweebo (dweebo@www.pente.org)
 */
public class DSG2_12GameFormat implements GameFormat {

    /** The name of the site this format is used for */
    public static final String SITE_NAME = "Pente.org";

    /** The format used for dates */
    private static final DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm");

    /** The line separator used by the format */
    private String lineSeparator;

    /** Create a new GameForamt.
     *  @param lineSeparator The line separator used by the format
     */
    public DSG2_12GameFormat(String lineSeparator) {
        this.lineSeparator = lineSeparator;
    }

    /** Format the game data into a buffer
     *  @param data The game data
     *  @param buffer The buffer to format into
     *  @return StringBuffer The buffer containing the formatted game
     */
    public StringBuffer format(Object data, StringBuffer buffer) {
        // not implemented
        return buffer;
    }

    /** Helper method to convert an Object into a GameData object
     *  @param obj The Object to convert
     *  @param GameData The converted Object
     */
    private GameData convertObject(Object obj) {

        if (obj == null) {
            return null;
        } else if (!(obj instanceof GameData)) {
            throw new IllegalArgumentException("Object not GameData");
        } else {
            return (GameData) obj;
        }
    }

    /** Parse the game data from a buffer
     *  @param data The game data to parse into
     *  @param buffer The buffer to parse from
     *  @return Object The game data parsed or null if the game doesn't play with
     *  the tournament rule.
     *  @exception ParseException If the game cannot be parsed
     */
    public Object parse(Object obj, StringBuffer buffer) throws ParseException {

        GameData data = convertObject(obj);

        int START = 0;
        int DONE_PLAYERS = 1;
        int DONE_DATE = 2;
        int DONE = 3;
        int state = START;

        StringTokenizer lineTokenizer = new StringTokenizer(buffer.toString(), lineSeparator);
        while (lineTokenizer.hasMoreTokens()) {

            String line = lineTokenizer.nextToken();

            // get the player information
            if (state == START) {

                StringTokenizer playerTokenizer = new StringTokenizer(line, " ");
                String player1Name = playerTokenizer.nextToken();
                playerTokenizer.nextToken();
                String player2Name = playerTokenizer.nextToken();

                // the * is used to represent the winner of the game
                if (player1Name.endsWith("*")) {
                    data.setWinner(1);
                    player1Name = player1Name.substring(0, player1Name.length() - 1);
                } else {
                    data.setWinner(2);
                    player2Name = player2Name.substring(0, player2Name.length() - 1);
                }

                data.getPlayer1Data().setUserIDName(player1Name);
                data.getPlayer2Data().setUserIDName(player2Name);

                state = DONE_PLAYERS;
            }
            // get the date information
            else if (state == DONE_PLAYERS) {

                // look for the date line
                int estIndex = line.indexOf(" EST");
                line = line.substring(0, estIndex);

                Date date = dateFormat.parse(line);
                data.setDate(date);

                state = DONE_DATE;
            }
            // get the moves of the game
            else if (state == DONE_DATE) {

                StringTokenizer moveTokenizer = new StringTokenizer(line, " ");
                while (moveTokenizer.hasMoreElements()) {
                    int move = PGNGameFormat.parseCoordinates(moveTokenizer.nextToken());

                    // check if this game was played with the tournament rule
                    // if not, then return null and print the offending move
                    if (data.getNumMoves() == 2) {
                        int x = move % 19;
                        int y = move / 19;
                        if ((x > 6 && x < 12 && y > 6 && y < 12)) {
                            System.out.println(PGNGameFormat.formatCoordinates(move) + "=" + x + "," + y);
                            return null;
                        }
                    }

                    data.addMove(move);
                }
            }
        }

        return data;
    }
}