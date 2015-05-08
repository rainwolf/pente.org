/** PBEMGameFormat.java
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

package org.pente.game;

import java.util.*;
import java.text.*;

/** A game formatter used to parse games stored in the format used
 *  at Richard's PBeM Server.
 *
 *  @author dweebo (dweebo@www.pente.org)
 */
public class PBEMGameFormat implements GameFormat {

    /** The name of the site this format is used for */
    public static final String SITE_NAME = "Richard's PBeM Server";

    /** The line separator used by the format */
    private String  lineSeparator;

    /** The format used for dates */
    private static final DateFormat     dateFormat = new SimpleDateFormat("MMM dd HH:mm");

    /** The year must be known by the client who is parsing the data
     *  since the year isn't included in the date in this format.
     */
    private int     year;


    /** Create with the specified line separator and year
     *  @param lineSeparator The line separator used by the format
     *  @param year The year this game was played in
     */
    public PBEMGameFormat(String lineSeparator, int year) {
        this.lineSeparator = lineSeparator;
        this.year = year;
    }


    /** Helper method to convert an Object into a GameData object
     *  @param obj The Object to convert
     *  @param GameData The converted Object
     */
    private GameData convertObject(Object obj) {

        if (obj == null) {
            return null;
        }
        else if (!(obj instanceof GameData)) {
            throw new IllegalArgumentException("Object not GameData");
        }
        else {
            return (GameData) obj;
        }
    }

    // not implemented
    public StringBuffer format(Object data, StringBuffer buffer) {
        return buffer;
    }

    /** Parse the game data from a buffer
     *  @param data The game data to parse into
     *  @param buffer The buffer to parse from
     *  @return Object The game data parsed
     *  @exception ParseException If the game cannot be parsed
     */
    public Object parse(Object obj, StringBuffer buffer) throws ParseException {

        GameData data = convertObject(obj);

        int lineNumber = 0;

        String winner = null;

        PlayerData player1Data = new DefaultPlayerData();
        PlayerData player2Data = new DefaultPlayerData();

        int START = 0;
        int DONE_RATINGS = 1;
        int DONE_PLAYERS = 2;
        int DONE_MOVES = 3;
        int END = 4;
        int state = START;

        data.setGame("Pro-Pente");
        data.setSite(SITE_NAME);

        StringTokenizer lineTokenizer = new StringTokenizer(buffer.toString(), lineSeparator);
        while (lineTokenizer.hasMoreTokens()) {

            String line = lineTokenizer.nextToken();

            if (state == START && line.startsWith("Game won by")) {
                winner = line.substring(12, line.length() - 1);
            }
            else if (state == START && line.indexOf("rating") != -1) {

                StringTokenizer ratingTokenizer = new StringTokenizer(line, " ");
                String nameStr = ratingTokenizer.nextToken();
                nameStr = nameStr.substring(0, nameStr.length() - 2);

                while (ratingTokenizer.hasMoreElements()) {

                    String ratingStr = ratingTokenizer.nextToken();
                    if (ratingStr.endsWith(".")) {
                        ratingStr = ratingStr.substring(0, ratingStr.length() - 1);
                    }

                    boolean isNumber = true;
                    int rating = 0;

                    try {
                        rating = Integer.parseInt(ratingStr);
                    } catch(NumberFormatException ex) {
                        isNumber = false;
                    }

                    if (isNumber) {
                        if (player1Data.getRating() == 0) {
                            player1Data.setRating(rating);
                            player1Data.setUserIDName(nameStr);
                            break;
                        }
                        else if (player2Data.getRating() == 0) {
                            player2Data.setRating(rating);
                            player2Data.setUserIDName(nameStr);
                            state = DONE_RATINGS;
                            break;
                        }
                    }
                }
            }
            else if (state == DONE_RATINGS) {

                line = lineTokenizer.nextToken();

                line = line.trim();
                int endPlayer1 = line.indexOf(" ");
                String player1 = line.substring(0, endPlayer1);
                String player2 = line.substring(endPlayer1).trim();

                if (player1.equals(player1Data.getUserIDName())) {
                    data.setPlayer1Data(player1Data);
                    data.setPlayer2Data(player2Data);
                }
                else {
                    data.setPlayer1Data(player2Data);
                    data.setPlayer2Data(player1Data);
                }

                if (data.getPlayer1Data().getUserIDName().equals(winner)) {
                    data.setWinner(GameData.PLAYER1);
                }
                else {
                    data.setWinner(GameData.PLAYER2);
                }

                state = DONE_PLAYERS;
            }
            else if (state == DONE_PLAYERS) {

                if (line.startsWith(data.getPlayer1Data().getUserIDName())) {
                    state = DONE_MOVES;
                }
                else {

                    StringTokenizer moveTokenizer = new StringTokenizer(line, " ");

                    boolean skip = true;
                    while (moveTokenizer.hasMoreTokens()) {

                        String moveStr = moveTokenizer.nextToken();

                        if (skip) {
                            skip = false;
                            continue;
                        }
                        else {
                            skip = true;
                        }

                        if (!moveStr.equals("Resign") && !moveStr.equals("Forfeit")) {
                            data.addMove(PGNGameFormat.parseCoordinates(moveStr));
                        }
                    }
                }
            }
            else if (state == DONE_MOVES) {

                if (line.startsWith("Game ended......")) {

                    line = line.substring(17);
                    Date endDate = dateFormat.parse(line);

                    Calendar endCal = new GregorianCalendar();
                    endCal.setTime(endDate);
                    endCal.set(Calendar.YEAR, year);
                    data.setDate(endCal.getTime());
                }
            }
        }

        if (data.getDate() == null) {
            return null;
        }

        return data;
    }
}