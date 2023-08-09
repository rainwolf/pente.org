/**
 * IYTUserProfileFilter.java
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

import org.pente.filter.*;
import org.pente.game.*;

/** LineFilter used to get info about a player from the user profile at iyt
 *  @since 0.2
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public class IYTUserProfileFilter implements LineFilter {

    /** String to look for to get the users userIDName */
    private static final String USER_ID_NAME = "User ID:";
    /** Html tag that the users userIDName is in */
    private static final String USER_ID_NAME_TAG = "b";
    /** String to look for to get the users name */
    private static final String PLAYER_NAME = "Name:";
    /** Html tag that the users name is in */
    private static final String PLAYER_NAME_TAG = "b";

    /** The player data found */
    private PlayerData playerData;

    /** Flag to tell if all info has been found, if true then
     *  filterLine() requests will just return the original line
     */
    private boolean done = false;

    /** The previous filter to call before filtering */
    private LineFilter prevFilter;

    /** Use this constructor if this is the base filter in your chain of filters
     *  @param playerData The PlayerData to put info into
     */
    public IYTUserProfileFilter(PlayerData playerData) {
        this(null, playerData);
    }

    /** Use this constructor if this is not the base filter in your chain of filters
     *  @param prevFilter The filter to call before filtering
     *  @param playerData The PlayerData to put info into
     */
    public IYTUserProfileFilter(LineFilter prevFilter, PlayerData playerData) {
        this.prevFilter = prevFilter;
        this.playerData = playerData;
    }

    /** Perform filtering on a line; look for user id name and name
     *  @param line The line to filter
     *  @return String The filtered line
     */
    public String filterLine(String line) {

        // if there is a previous filter, allow it to filter before
        // doing any other filtering.
        if (prevFilter != null) {
            line = prevFilter.filterLine(line);
        }
        // if the line is null or we are done looking for data, don't do anything
        if (line != null && !done) {

            // if haven't found user id name yet, look for it
            if (playerData.getUserIDName() == null) {
                playerData.setUserIDName(getInfo(line, USER_ID_NAME, USER_ID_NAME_TAG));
            }

            // if found user id name, we're done!
            if (playerData.getUserIDName() != null) {
                done = true;
            }
        }

        return line;
    }

    /** Looks for information in a line of html
     *  @param line The line to look in
     *  @param name A string that precedes the info we are looking for
     *  @param enclosingTag After we find the 'name', the info we are
     *  looking for should be inside a html tag, like <b>info</b>
     *  @return String The info we are looking for, null if not found
     */
    private String getInfo(String line, String name, String enclosingTag) {

        // make the line lowercase
        String lineLower = line.toLowerCase();

        // look for the 'name' in the line
        int index = line.indexOf(name);
        if (index < 0) return null;

        // look for the start of the 'enclosingTag' in the line
        int tagBegin = lineLower.indexOf("<" + enclosingTag, index + 1);
        if (tagBegin < 0) return null;

        // look for the end of the start of the 'enclosingTag' in the line
        tagBegin = lineLower.indexOf(">", tagBegin + 1);
        if (tagBegin < 0) return null;
        tagBegin++;

        // look for the begin of the end tag in the line
        int tagEnd = lineLower.indexOf("</" + enclosingTag + ">", tagBegin + 1);
        if (tagEnd < 0) return null;

        // return the info
        return line.substring(tagBegin, tagEnd).trim();
    }
}