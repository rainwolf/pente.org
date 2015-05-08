/** IYTGameData.java
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

import org.pente.filter.iyt.*;
import org.pente.game.*;

/** IYT extension of DefaultGameData.
 *
 *  Note added on 03/17/2001.  I think this should only be used when getting
 *  data from IYT (prior to storing in a game storer).  Once the game is stored
 *  and loaded in again, this class doesn't add anything.
 *
 *  @since 0.2
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public class IYTGameData extends DefaultGameData {

    /** Site is always iyt */
    public IYTGameData() {
        site = IYTConstants.SITE_NAME;
    }

    /** Game is always timed if the event is a tournament game
     *  and not timed if the event is not a tournament game
     *  @return boolean Timed flag
     */
    public boolean getTimed() {
        return event != null && !event.equals("Non-Tournament Game");
    }

    /** Game is always rated if the event is a tournament game
     *  and not rated if the event is not a tournament game
     *  @return boolean Rated flag
     */
    public boolean getRated() {
        return event != null && !event.equals("Non-Tournament Game");
    }
}