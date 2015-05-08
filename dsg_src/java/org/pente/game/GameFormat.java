/** GameFormat.java
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

import java.text.*;

/** Interface to classes that know how to format and parse games from/into
 *  GameData information.
 *  @since 0.2
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public interface GameFormat extends ObjectFormat {

    /** Format the game data into a buffer
     *  @param data The game data
     *  @param buffer The buffer to format into
     *  @return StringBuffer The buffer containing the formatted game
     */
    public StringBuffer format(Object data, StringBuffer buffer);

    /** Parse the game data from a buffer
     *  @param data The game data to parse into
     *  @param buffer The buffer to parse from
     *  @return Object The game data parsed
     *  @exception ParseException If the game cannot be parsed
     */
    public Object parse(Object data, StringBuffer buffer) throws ParseException;
}