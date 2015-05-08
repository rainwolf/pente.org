package org.pente.filter.brainking;

import org.pente.game.*;
import org.pente.filter.*;

public class BKUserProfileFilter implements LineFilter {

    protected String USER_ID_NAME = "Profile:";

    private PlayerData playerData;

    private boolean done = false;

    private LineFilter prevFilter;


    public BKUserProfileFilter(PlayerData playerData) {
        this(null, playerData);
    }

    public BKUserProfileFilter(LineFilter prevFilter, PlayerData playerData) {
        this.prevFilter = prevFilter;
        this.playerData = playerData;
    }

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
                playerData.setUserIDName(getInfo(line, USER_ID_NAME));
            }

            // if found user id name, we're done!
            if (playerData.getUserIDName() != null) {
                done = true;
            }
        }

        return line;
    }

    protected String getInfo(String line, String name) {

        // make the line lowercase
        String lineLower = line.toLowerCase();

        // look for the 'name' in the line
        int index = line.indexOf(name);
        if (index < 0) return null;

        int tagBegin = index + name.length() + 1;

        // look for the begin of the end tag in the line
        int tagEnd = lineLower.indexOf("</b>", tagBegin + 1);
        if (tagEnd < 0) return null;

        // return the info
        return line.substring(tagBegin, tagEnd).trim();
    }
}
