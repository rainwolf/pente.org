package org.pente.filter.brainking;

import java.util.*;

import org.pente.filter.*;
import org.pente.filter.http.*;
import org.pente.game.*;

public class BKUserProfileBuilder implements FilterListener, Runnable {

    private BKUserProfileFilter bkUserProfileFilter;

    private Hashtable cookies;
    private Hashtable params;

    private PlayerData playerData;

    public static void main(String args[]) throws Exception {

        if (args.length != 2) {
            System.err.println("usage: BKUserProfileBuilder <player> <cookie>");
        } else {
            String playerID = args[0];
            String cookie = args[1];

            BKUserProfileBuilder builder = new BKUserProfileBuilder(playerID, cookie);
            builder.run();

            System.out.println(builder.getPlayerData().getUserIDName());
        }
    }

    public BKUserProfileBuilder(String playerID, String cookie) {
        init(playerID, cookie);
    }

    /*
        public BKUserProfileBuilder(PlayerData playerData, Hashtable cookies) {

            this.playerData = playerData;
            this.cookies = cookies;

            params = new Hashtable();
            params.put(BKConstants.USER_PROFILE_PARAM, Long.toString(playerData.getUserID()));

            bkUserProfileFilter = new BKUserProfileFilter(playerData);
        }
    */
    private void init(String playerID, String cookie) {

        BKLogin login = new BKLogin(cookie);
        cookies = login.getCookies();

        playerData = new DefaultPlayerData();
        playerData.setUserID(Long.parseLong(playerID));

        params = new Hashtable();
        params.put(BKConstants.USER_PROFILE_PARAM, playerID);

        bkUserProfileFilter = new BKUserProfileFilter(playerData);
    }

    /**
     * Return the player data
     *
     * @return PlayerData The player data built
     */
    public PlayerData getPlayerData() {
        return playerData;
    }

    /**
     * Could be run in a new thread
     * Sends a request to iyt to get the user profile screen and filter it
     */
    public synchronized void run() {

        FilterController filterController = new HttpFilterController(
                "GET",
                BKConstants.HOST,
                BKConstants.USER_PROFILE_REQUEST,
                params,
                cookies,
                bkUserProfileFilter);
        filterController.run();
    }

    public void lineFiltered(String line) {
    }

    public void filteringComplete(boolean success, Exception ex) {
    }
}
