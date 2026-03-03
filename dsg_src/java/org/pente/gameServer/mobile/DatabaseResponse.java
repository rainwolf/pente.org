package org.pente.gameServer.mobile;

import org.pente.game.GameData;
import org.pente.gameDatabase.GameStorerSearchResponseData;
import org.pente.gameDatabase.GameStorerSearchResponseMoveData;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * Serializes game database search results (database.jsp).
 */
public class DatabaseResponse {

    public final boolean access;
    public final boolean blocked;
    public final List<Integer> moves;
    public final List<String> occurrence;
    public final List<GameEntry> games;

    public static class PlayerRef {
        public final String name;
        public final String url;
        public final boolean winner;

        PlayerRef(String name, String url, boolean winner) {
            this.name = name;
            this.url = url;
            this.winner = winner;
        }
    }

    public static class GameEntry {
        public final long gameId;
        public final String event;
        public final String date;
        public final String viewUrl;
        public final String site;
        public final PlayerRef player1;
        public final PlayerRef player2;

        GameEntry(long gameId, String event, String date, String viewUrl, String site,
                  PlayerRef player1, PlayerRef player2) {
            this.gameId = gameId;
            this.event = event;
            this.date = date;
            this.viewUrl = viewUrl;
            this.site = site;
            this.player1 = player1;
            this.player2 = player2;
        }
    }

    private DatabaseResponse(boolean access, boolean blocked,
                             List<Integer> moves, List<String> occurrence,
                             List<GameEntry> games) {
        this.access = access;
        this.blocked = blocked;
        this.moves = moves;
        this.occurrence = occurrence;
        this.games = games;
    }

    public static DatabaseResponse noAccess() {
        return new DatabaseResponse(false, false,
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    public static DatabaseResponse blocked() {
        return new DatabaseResponse(true, true,
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    /**
     * @param data         search response containing moves and game list
     * @param contextPath  request context path for building Pente.org profile URLs
     */
    public static DatabaseResponse build(GameStorerSearchResponseData data, String contextPath) {
        NumberFormat percentFormat = NumberFormat.getPercentInstance();
        percentFormat.setMaximumFractionDigits(1);
        DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");

        int responseOrder = data.getGameStorerSearchRequestData()
                .getGameStorerSearchResponseOrder() + 1;

        Vector searchResultsVector = data.searchResponseMoveData();

        // Compute total for percentage-by-games-count mode
        double total = 0;
        if (responseOrder == 2) {
            for (Object o : searchResultsVector) {
                total += ((GameStorerSearchResponseMoveData) o).getGames();
            }
        }
        if (total == 0) total = 100;

        // Moves
        List<Integer> movesList = new ArrayList<>();
        for (Object o : searchResultsVector) {
            movesList.add(((GameStorerSearchResponseMoveData) o).getMove());
        }

        // Occurrence percentages
        List<String> occurrenceList = new ArrayList<>();
        for (Object o : searchResultsVector) {
            GameStorerSearchResponseMoveData md = (GameStorerSearchResponseMoveData) o;
            String pct = responseOrder == 2
                    ? percentFormat.format((double) md.getGames() / total)
                    : percentFormat.format(md.getPercentage());
            occurrenceList.add(pct.replace("%", ""));
        }

        // Games
        List<GameEntry> gameEntries = new ArrayList<>();
        for (Object o : data.getGames()) {
            GameData gd = (GameData) o;
            String p1Link = profileUrl(gd, true, contextPath);
            String p2Link = profileUrl(gd, false, contextPath);
            gameEntries.add(new GameEntry(
                    gd.getGameID(),
                    gd.getEvent(),
                    dateFormat.format(gd.getDate()),
                    "/gameServer/viewLiveGame?mobile&g=" + gd.getGameID(),
                    gd.getShortSite(),
                    new PlayerRef(gd.getPlayer1Data().getUserIDName(), p1Link,
                            gd.getWinner() == GameData.PLAYER1),
                    new PlayerRef(gd.getPlayer2Data().getUserIDName(), p2Link,
                            gd.getWinner() == GameData.PLAYER2)
            ));
        }

        return new DatabaseResponse(true, false, movesList, occurrenceList, gameEntries);
    }

    private static String profileUrl(GameData gd, boolean player1, String contextPath) {
        String site = gd.getShortSite();
        String name = player1
                ? gd.getPlayer1Data().getUserIDName()
                : gd.getPlayer2Data().getUserIDName();
        long userId = player1
                ? gd.getPlayer1Data().getUserID()
                : gd.getPlayer2Data().getUserID();

        if ("Pente.org".equals(site)) {
            return "/gameServer/profile?viewName=" + name;
        } else if ("IYT".equals(site)) {
            return "http://www.itsyourturn.com/iyt.dll?userprofile?userid=" + userId;
        } else if ("BK".equals(site)) {
            return "http://brainking.com/game/PlayerList?submit=Search&a=ap&utf=" + name;
        }
        return gd.getSiteURL();
    }
}