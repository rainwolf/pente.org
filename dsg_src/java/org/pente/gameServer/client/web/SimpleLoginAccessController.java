package org.pente.gameServer.client.web;

public class SimpleLoginAccessController implements LoginAccessController {

    private static final String loginPages[] = new String[]
            {"gameServer/play.jsp",
                    "gameServer/myprofile",
                    "gameServer/myprofile/donor",
                    "gameServer/newMessage.jsp",
                    "gameServer/mymessages",
                    "gameServer/profile.jsp",
                    "gameServer/profile",
                    "gameServer/deletePlayer",
                    "gameServer/deletePlayer.jsp",
                    "gameServer/who.jsp",
                    "gameServer/tournaments/tournamentSignup.jsp",
                    "gameServer/forums/settings!tab.jspa",
                    "gameServer/forums/editwatches!default.jspa",
                    "gameServer/viewLiveGames",
                    "gameServer/viewLiveGame",
                    "gameServer/pentedb",
                    "gameServer/pgn.jsp",
                    "gameServer/tbpgn.jsp",
                    "gameServer/googlePlayPurchase",
                    "gameServer/appStorePurchase",
                    "gameServer/ignored",
                    "gameServer/messageJson"
            };

    private static final String restrictedPages[] = new String[]
            {"servlet/ChangeProfileServlet", "servlet/EmailPlayerServlet"};

    public boolean requiresLogin(String resource) {

        if (resource == null) {
            return false;
        }

        for (String page : loginPages) {
            if (resource.endsWith(page)) {
                return true;
            }
        }

        // restrict games history
        if (resource.indexOf("controller") > -1) {
            return true;
        }
        // restrict turn based pages
        if (resource.indexOf("/tb/") > -1) {
            return true;
        }

        return false;
    }

    public boolean isRestricted(String resource) {

        if (resource == null) {
            return false;
        }

        for (String page : restrictedPages) {
            if (resource.endsWith(page)) {
                return true;
            }
        }

        return false;
    }

    public boolean requiresAdmin(String resource) {

        if (resource == null) {
            return false;
        } else if (resource.indexOf("admin") > -1) {
            return true;
        }

        return false;
    }
}