/** IYTPGNGameFormat.java
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

package org.pente.filter.iyt.game;

import org.pente.game.*;

/** IYT PNG Game Format, formats games with information specific to IYT.
 *  @since 0.2
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public class IYTPGNGameFormat extends PGNGameFormat {

    /** Use a default line separator */
    public IYTPGNGameFormat() {
        super("\r\n");
    }

    /** Specify the line seperator used to format games
     *  @param lineSeparator The line separator
     */
    public IYTPGNGameFormat(String lineSeparator) {
        super(lineSeparator);
    }


    /** Format a string representation of the event.
     *  For iyt if the event is null, return "Non-Tournament Game"
     *  @param data The game data
     *  @return String The event
     */
    protected String formatEvent(GameData data) {
        return formatHeader(HEADER_EVENT, (data.getEvent() == null ? "Non-Tournament Game" : data.getEvent()));
    }

    /** Parse a string representation of the event
     *  @param event The event
     *  @param data The game data
     */
    protected void parseEvent(String event, GameData data) {

        if (event != null && event.equals("Non-Tournament Game")) {
            event = null;
        }
        data.setEvent(event);
    }


    /** Format a string representation of the time control.
     *  For iyt provide a more descriptive time control
     *  @param data The game data
     *  @return String The time control
     */
    protected String formatTimeControl(GameData data) {

        String timeControl = "-";
        if (data.getEvent() == null) {
            timeControl = "-";
        }
        else if (data.getEvent().indexOf("Main") != -1) {
            timeControl = "48 hours/move (Main)";
        }
        else if (data.getEvent().indexOf("Fast") != -1) {
            timeControl = "28 hours/move (Fast)";
        }

        return formatHeader(HEADER_TIME_CONTROL, timeControl);
    }

    /** Parse a string representation of the time control.
     *  @return timeControl The time control
     *  @param data The game data
     */
    protected void parseTimeControl(String timeControl, GameData data) {

        if (timeControl != null) {

            if (timeControl.equals("-")) {
                timeControl = null;
            }
            else {

                int time = 0;
                if (timeControl.indexOf("Main") != -1) {
                    time = 48;
                }
                else if (timeControl.indexOf("Fast") != -1) {
                    time = 28;
                }

                data.setInitialTime(time);
                data.setIncrementalTime(time);
            }
        }

        data.setTimed(timeControl != null);
    }
}