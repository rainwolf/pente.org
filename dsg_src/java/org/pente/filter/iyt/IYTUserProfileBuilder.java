/**
 * IYTUserProfileBuilder.java
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

import java.util.*;

import org.pente.filter.*;
import org.pente.filter.http.*;
import org.pente.game.*;

/** Gets information about a player from the user profile at iyt
 *  and puts it in a PlayerData object.
 *  @since 0.2
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public class IYTUserProfileBuilder implements FilterListener, Runnable {

    /** Filter that does the work of finding player data */
    private IYTUserProfileFilter iytUserProfileFilter;

    /** Cookies used to send requests to iyt */
    private Hashtable<String, String> cookies;
    /** Parameters used to send requests to iyt */
    private Hashtable<String, String> params;

    /** The player data found */
    private PlayerData playerData;

    /** For testing purposes you can call this builder directly
     *  with your cookie info to attempt to get your player info
     *  @param args[] args[0] should be your cookie
     */
    public static void main(String args[]) throws Exception {

        if (args.length != 1) {
            System.err.println("usage: IYTUserProfileBuilder <cookie>");
        } else {
            String cookie = args[0];

            IYTUserProfileBuilder builder = new IYTUserProfileBuilder(cookie);
            builder.run();

            System.out.println(builder.getPlayerData().getUserIDName());
        }
    }

    /** Use this constructor if you have the cookie used by the player
     *  This will get player data on the user indicated by the cookie
     *  @param cookie The cookie string used for iyt
     */
    public IYTUserProfileBuilder(String cookie) {
        Hashtable<String, String> cookies = new Hashtable<>();
        cookies.put(IYTConstants.USERID_COOKIE, cookie);

        String userID = cookie.substring(0, 14);

        init(userID, cookies);
    }

    /** Use this constructor if you have the user id of another
     *  player.
     *  @param userID The user id of the other player
     *  @param cookies The cookies to log into iyt.
     */
    public IYTUserProfileBuilder(String userID, Hashtable<String, String> cookies) {
        init(userID, cookies);
    }

    /** Use this constructor if you just need some additional info
     *  @param playerData The player data you have so far
     */
    public IYTUserProfileBuilder(PlayerData playerData, Hashtable<String, String> cookies) {

        this.playerData = playerData;
        this.cookies = cookies;

        params = new Hashtable<>();
        params.put(IYTConstants.USERID_PARAMETER, Long.toString(playerData.getUserID()));

        iytUserProfileFilter = new IYTUserProfileFilter(playerData);
    }

    /** Common initialization for some constructors
     *  @param userID The user id of the other player
     *  @param cookies The cookies to log into iyt.
     */
    private void init(String userID, Hashtable<String, String> cookies) {

        this.cookies = cookies;

        params = new Hashtable<>();
        params.put(IYTConstants.USERID_PARAMETER, userID);

        playerData = new DefaultPlayerData();
        playerData.setUserID(Long.parseLong(userID));
        iytUserProfileFilter = new IYTUserProfileFilter(playerData);
    }

    /** Return the player data
     *  @return PlayerData The player data built
     */
    public PlayerData getPlayerData() {
        return playerData;
    }

    /** Could be run in a new thread
     *  Sends a request to iyt to get the user profile screen and filter it
     */
    public synchronized void run() {

        FilterController filterController = new HttpFilterController("GET",
                IYTConstants.HOST,
                IYTConstants.USER_PROFILE_REQUEST,
                params,
                cookies,
                iytUserProfileFilter);
        filterController.run();

        // if the players user id name wasn't found, try looking again with the userid
        // less one character.  this is done because userid's can be 14 OR 13 characters
        // and i can't tell which when getting the userid from the cookie
        // i haven't tested this, since my userid is 14 long... but maybe it will work
        if (playerData.getUserIDName() == null) {

            String userID = Long.toString(playerData.getUserID()).substring(0, 13);
            playerData.setUserID(Long.parseLong(userID));

            filterController.run();
        }
    }

    /** For future compatibility if the builder wants to run() in its own thread.
     *  @param line The filtered line
     */
    public void lineFiltered(String line) {
    }

    /** For future compatibility if the builder wants to run() in its own thread.
     *  @param success Whether or not the filtering was successful
     *  @param ex The exception that occurred if !success
     */
    public void filteringComplete(boolean success, Exception ex) {
    }
}