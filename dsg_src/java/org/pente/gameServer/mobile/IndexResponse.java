package org.pente.gameServer.mobile;

import org.pente.game.Game;
import org.pente.game.GridStateFactory;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerGameData;
import org.pente.gameServer.core.DSGPlayerStoreException;
import org.pente.gameServer.core.DSGPlayerStorer;
import org.pente.gameServer.tourney.Tourney;
import org.pente.gameServer.tourney.TourneyStorer;
import org.pente.kingOfTheHill.CacheKOTHStorer;
import org.pente.kingOfTheHill.Hill;
import org.pente.message.DSGMessage;
import org.pente.turnBased.TBGame;
import org.pente.turnBased.TBSet;
import org.pente.turnBased.Utilities;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/**
 * Serializes the main mobile index response (index.jsp).
 *
 * <p>Use {@link Builder} to construct an instance, then serialize with Gson.
 * The caller is responsible for all data loading and filtering that the JSP performs
 * (waiting-set filtering, game organizing, etc.) before handing data to the builder.
 */
public class IndexResponse {

    public final Settings settings;
    public final PlayerInfo player;
    public final List<KothEntry> kingOfTheHill;
    public final List<RatingStatEntry> ratingStats;
    public final List<InvitationEntry> invitationsReceived;
    public final List<InvitationEntry> invitationsSent;
    public final List<GameEntry> activeGamesMyTurn;
    public final List<GameEntry> activeGamesOpponentTurn;
    public final List<OpenInvitationEntry> openInvitationGames;
    public final List<MessageEntry> messages;
    public final List<TournamentEntry> tournaments;
    public final List<String> onlinePlayers;

    // ── inner POJOs ────────────────────────────────────────────────────────────

    public static class Settings {
        public final boolean noAds;
        public final boolean unlimitedGames;
        /**
         * -1 when there is no limit (unlimited plan).
         */
        public final int tbGamesLimit;

        Settings(boolean noAds, boolean unlimitedGames, int tbGamesLimit) {
            this.noAds = noAds;
            this.unlimitedGames = unlimitedGames;
            this.tbGamesLimit = tbGamesLimit;
        }
    }

    public static class PlayerInfo {
        public final String name;
        public final int color;
        public final boolean showAds;
        public final boolean subscriber;
        public final int livePlayers;
        public final boolean dbAccess;
        public final boolean emailMe;
        public final int onlineFollowing;
        public final boolean personalizeAds;

        PlayerInfo(String name, int color, boolean showAds, boolean subscriber,
                   int livePlayers, boolean dbAccess, boolean emailMe,
                   int onlineFollowing, boolean personalizeAds) {
            this.name = name;
            this.color = color;
            this.showAds = showAds;
            this.subscriber = subscriber;
            this.livePlayers = livePlayers;
            this.dbAccess = dbAccess;
            this.emailMe = emailMe;
            this.onlineFollowing = onlineFollowing;
            this.personalizeAds = personalizeAds;
        }
    }

    public static class KothEntry {
        public final int numPlayers;
        public final boolean amIMember;
        public final boolean iAmKing;
        public final String kingName;
        public final boolean canChallenge;
        public final int gameId;

        KothEntry(int numPlayers, boolean amIMember, boolean iAmKing,
                  String kingName, boolean canChallenge, int gameId) {
            this.numPlayers = numPlayers;
            this.amIMember = amIMember;
            this.iAmKing = iAmKing;
            this.kingName = kingName;
            this.canChallenge = canChallenge;
            this.gameId = gameId;
        }
    }

    public static class RatingStatEntry {
        public final String gameName;
        public final int rating;
        public final int totalGames;
        public final int tourneyWinner;
        public final String lastGameDate;
        public final int gameId;

        RatingStatEntry(String gameName, int rating, int totalGames,
                        int tourneyWinner, String lastGameDate, int gameId) {
            this.gameName = gameName;
            this.rating = rating;
            this.totalGames = totalGames;
            this.tourneyWinner = tourneyWinner;
            this.lastGameDate = lastGameDate;
            this.gameId = gameId;
        }
    }

    public static class InvitationEntry {
        public final long setId;
        public final String gameName;
        public final String opponentName;
        public final int opponentRating;
        public final String color;
        public final int daysPerMove;
        public final String rated;
        public final int opponentColor;
        public final int opponentTourneyWinner;

        InvitationEntry(long setId, String gameName, String opponentName, int opponentRating,
                        String color, int daysPerMove, String rated,
                        int opponentColor, int opponentTourneyWinner) {
            this.setId = setId;
            this.gameName = gameName;
            this.opponentName = opponentName;
            this.opponentRating = opponentRating;
            this.color = color;
            this.daysPerMove = daysPerMove;
            this.rated = rated;
            this.opponentColor = opponentColor;
            this.opponentTourneyWinner = opponentTourneyWinner;
        }
    }

    public static class GameEntry {
        public final long gid;
        public final String gameName;
        public final String opponentName;
        public final int opponentRating;
        public final String color;
        public final int numMoves;
        public final String timeLeft;
        public final String rated;
        public final int opponentColor;
        public final int opponentTourneyWinner;

        GameEntry(long gid, String gameName, String opponentName, int opponentRating,
                  String color, int numMoves, String timeLeft, String rated,
                  int opponentColor, int opponentTourneyWinner) {
            this.gid = gid;
            this.gameName = gameName;
            this.opponentName = opponentName;
            this.opponentRating = opponentRating;
            this.color = color;
            this.numMoves = numMoves;
            this.timeLeft = timeLeft;
            this.rated = rated;
            this.opponentColor = opponentColor;
            this.opponentTourneyWinner = opponentTourneyWinner;
        }
    }

    public static class OpenInvitationEntry {
        public final long setId;
        public final String gameName;
        public final String inviterName;
        public final int inviterRating;
        public final String color;
        public final int daysPerMove;
        public final String rated;
        public final int inviterColor;
        public final int inviterTourneyWinner;

        OpenInvitationEntry(long setId, String gameName, String inviterName, int inviterRating,
                            String color, int daysPerMove, String rated,
                            int inviterColor, int inviterTourneyWinner) {
            this.setId = setId;
            this.gameName = gameName;
            this.inviterName = inviterName;
            this.inviterRating = inviterRating;
            this.color = color;
            this.daysPerMove = daysPerMove;
            this.rated = rated;
            this.inviterColor = inviterColor;
            this.inviterTourneyWinner = inviterTourneyWinner;
        }
    }

    public static class MessageEntry {
        public final int mid;
        public final boolean read;
        public final String subject;
        public final String from;
        public final String date;
        public final int fromColor;
        public final int fromTourneyWinner;

        MessageEntry(int mid, boolean read, String subject, String from,
                     String date, int fromColor, int fromTourneyWinner) {
            this.mid = mid;
            this.read = read;
            this.subject = subject;
            this.from = from;
            this.date = date;
            this.fromColor = fromColor;
            this.fromTourneyWinner = fromTourneyWinner;
        }
    }

    public static class TournamentEntry {
        public final String name;
        public final long eventId;
        public final int numRounds;
        public final String gameName;
        /**
         * 1 = upcoming (signup open), 2 = current (in progress, no rounds), 3 = current (in progress).
         */
        public final int status;
        public final String date;

        TournamentEntry(String name, long eventId, int numRounds,
                        String gameName, int status, String date) {
            this.name = name;
            this.eventId = eventId;
            this.numRounds = numRounds;
            this.gameName = gameName;
            this.status = status;
            this.date = date;
        }
    }

    // ── private constructor ────────────────────────────────────────────────────

    private IndexResponse(Settings settings, PlayerInfo player,
                          List<KothEntry> kingOfTheHill, List<RatingStatEntry> ratingStats,
                          List<InvitationEntry> invitationsReceived,
                          List<InvitationEntry> invitationsSent,
                          List<GameEntry> activeGamesMyTurn,
                          List<GameEntry> activeGamesOpponentTurn,
                          List<OpenInvitationEntry> openInvitationGames,
                          List<MessageEntry> messages, List<TournamentEntry> tournaments,
                          List<String> onlinePlayers) {
        this.settings = settings;
        this.player = player;
        this.kingOfTheHill = kingOfTheHill;
        this.ratingStats = ratingStats;
        this.invitationsReceived = invitationsReceived;
        this.invitationsSent = invitationsSent;
        this.activeGamesMyTurn = activeGamesMyTurn;
        this.activeGamesOpponentTurn = activeGamesOpponentTurn;
        this.openInvitationGames = openInvitationGames;
        this.messages = messages;
        this.tournaments = tournaments;
        this.onlinePlayers = onlinePlayers;
    }

    // ── Builder ────────────────────────────────────────────────────────────────

    /**
     * Fluent builder for {@link IndexResponse}.
     *
     * <p>Callers must set all required fields before calling {@link #build()}.
     * All list-type data (games, invitations, etc.) should already be filtered
     * to match the business rules enforced in the JSP.
     */
    public static class Builder {

        private final DSGPlayerData playerData;
        private final String playerName;           // normalized lowercase
        private final DSGPlayerStorer playerStorer;
        private final CacheKOTHStorer kothStorer;
        private final TourneyStorer tourneyStorer;
        private final List<Tourney> currentTournies;

        // Online stats
        private int livePlayers;
        private int onlineFollowing;
        private List<String> onlinePlayerNames = new ArrayList<>();

        // Preferences
        private boolean emailMe = true;
        private boolean personalizeAds = false;

        // Games limit
        private int gamesLimit = 4;
        private boolean hasTbGamesLimit = true;

        // Game lists (pre-filtered by caller)
        private List<TBGame> myTurn = new ArrayList<>();
        private List<TBGame> oppTurn = new ArrayList<>();
        private List<TBSet> invitesTo = new ArrayList<>();
        private List<TBSet> invitesFrom = new ArrayList<>();
        private List<TBSet> waitingSets = new ArrayList<>();

        // Messages
        private List<DSGMessage> messages = new ArrayList<>();
        private TimeZone messageTimezone = TimeZone.getDefault();

        // Upcoming tournaments
        private List<Tourney> upcomingTournies = new ArrayList<>();

        public Builder(DSGPlayerData playerData, String playerName,
                       DSGPlayerStorer playerStorer, CacheKOTHStorer kothStorer,
                       TourneyStorer tourneyStorer, List<Tourney> currentTournies) {
            this.playerData = playerData;
            this.playerName = playerName;
            this.playerStorer = playerStorer;
            this.kothStorer = kothStorer;
            this.tourneyStorer = tourneyStorer;
            this.currentTournies = currentTournies;
        }

        public Builder setOnlineStats(int livePlayers, int onlineFollowing,
                                      List<String> onlinePlayerNames) {
            this.livePlayers = livePlayers;
            this.onlineFollowing = onlineFollowing;
            this.onlinePlayerNames = onlinePlayerNames;
            return this;
        }

        public Builder setPreferences(boolean emailMe, boolean personalizeAds,
                                      int gamesLimit, boolean hasTbGamesLimit) {
            this.emailMe = emailMe;
            this.personalizeAds = personalizeAds;
            this.gamesLimit = gamesLimit;
            this.hasTbGamesLimit = hasTbGamesLimit;
            return this;
        }

        public Builder setGames(List<TBGame> myTurn, List<TBGame> oppTurn,
                                List<TBSet> invitesTo, List<TBSet> invitesFrom,
                                List<TBSet> waitingSets) {
            this.myTurn = myTurn;
            this.oppTurn = oppTurn;
            this.invitesTo = invitesTo;
            this.invitesFrom = invitesFrom;
            this.waitingSets = waitingSets;
            return this;
        }

        public Builder setMessages(List<DSGMessage> messages, TimeZone timezone) {
            this.messages = messages;
            this.messageTimezone = timezone;
            return this;
        }

        public Builder setUpcomingTournies(List<Tourney> upcomingTournies) {
            this.upcomingTournies = upcomingTournies;
            return this;
        }

        public IndexResponse build() throws Throwable {
            boolean subscriber = playerData.hasPlayerDonated();
            boolean isRainwolf = "rainwolf".equals(playerName);
            boolean noAds = isRainwolf || !playerData.showAds();
            boolean dbAccess = subscriber
                    || playerData.getRegisterDate().getTime()
                    > System.currentTimeMillis() - 1000L * 3600 * 24 * 30;

            DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
            DateFormat messageDateFormat = new SimpleDateFormat("MM/dd/yy");
            messageDateFormat.setTimeZone(messageTimezone);

            long myPID = playerData.getPlayerID();

            return new IndexResponse(
                    buildSettings(noAds),
                    new PlayerInfo(
                            playerData.getName().toLowerCase(),
                            subscriber ? playerData.getNameColorRGB() : 0,
                            false,   // showAds always false per JSP override
                            subscriber,
                            livePlayers,
                            dbAccess,
                            emailMe,
                            onlineFollowing,
                            personalizeAds
                    ),
                    buildKoth(myPID, subscriber),
                    buildRatingStats(dateFormat),
                    buildInvitesReceived(myPID),
                    buildInvitesSent(myPID),
                    buildGameEntries(myTurn, myPID),
                    buildGameEntries(oppTurn, myPID),
                    buildOpenInvitations(),
                    buildMessages(messageDateFormat),
                    buildTournaments(dateFormat),
                    new ArrayList<>(onlinePlayerNames)
            );
        }

        // ── private section builders ───────────────────────────────────────────

        private Settings buildSettings(boolean noAds) {
            return new Settings(noAds, true,
                    hasTbGamesLimit ? gamesLimit : -1);
        }

        private List<KothEntry> buildKoth(long myPID, boolean subscriber) throws DSGPlayerStoreException {
            List<KothEntry> result = new ArrayList<>();
            addKothEntries(result, GridStateFactory.TB_GAMES, myPID, subscriber, false);
            addKothEntries(result, GridStateFactory.LIVE_GAMES, myPID, subscriber, true);  // odd first
            return result;
        }

        private void addKothEntries(List<KothEntry> result, int[] gameIds,
                                    long myPID, boolean subscriber, boolean liveGames) throws DSGPlayerStoreException {
            // For live games the JSP outputs odd IDs first, then even IDs
            if (liveGames) {
                for (int gameInt : gameIds) {
                    if (gameInt % 2 == 0) continue;
                    addKothEntry(result, gameInt, myPID, subscriber);
                }
                for (int gameInt : gameIds) {
                    if (gameInt % 2 == 1) continue;
                    addKothEntry(result, gameInt, myPID, subscriber);
                }
            } else {
                for (int gameInt : gameIds) {
                    addKothEntry(result, gameInt, myPID, subscriber);
                }
            }
        }

        private void addKothEntry(List<KothEntry> result, int gameInt,
                                  long myPID, boolean subscriber) throws DSGPlayerStoreException {
            Hill hill = kothStorer.getHill(gameInt);
            if (hill == null) return;
            if (gameInt > 50 && (hill.getMembers() == null || hill.getMembers().isEmpty())) return;

            long kingPid = hill.getKing();
            boolean amImember = hill.hasPlayer(myPID);
            boolean canSendOpenKotH = subscriber
                    || kothStorer.canPlayerBeChallenged(gameInt, myPID);
            String kingName = "";
            if (kingPid != 0) {
                DSGPlayerData king = playerStorer.loadPlayer(kingPid);
                kingName = king != null ? king.getName() : "";
            }
            result.add(new KothEntry(
                    hill.getNumPlayers(),
                    amImember,
                    kingPid == myPID,
                    kingName,
                    amImember && canSendOpenKotH,
                    gameInt
            ));
        }

        private List<RatingStatEntry> buildRatingStats(DateFormat dateFormat) {
            List<RatingStatEntry> result = new ArrayList<>();
            addRatingStats(result, GridStateFactory.getTbGames(), dateFormat);
            addRatingStats(result, GridStateFactory.getNormalGames(), dateFormat);
            addRatingStats(result, GridStateFactory.getSpeedGames(), dateFormat);
            return result;
        }

        private void addRatingStats(List<RatingStatEntry> result, Game[] games,
                                    DateFormat dateFormat) {
            for (Game g : games) {
                DSGPlayerGameData gd = playerData.getPlayerGameData(g.getId());
                if (gd == null || gd.getTotalGames() == 0) continue;
                String displayName = (g.getId() > 50 ? "tb-" : "")
                        + GridStateFactory.getGameName(g.getId()).replace("Speed ", "Speed-");
                result.add(new RatingStatEntry(
                        displayName,
                        (int) Math.round(gd.getRating()),
                        gd.getTotalGames(),
                        gd.getTourneyWinner(),
                        dateFormat.format(gd.getLastGameDate()),
                        g.getId()
                ));
            }
        }

        private List<InvitationEntry> buildInvitesReceived(long myPID) throws DSGPlayerStoreException {
            List<InvitationEntry> result = new ArrayList<>();
            for (TBSet s : invitesTo) {
                TBGame g = s.getGame1();
                boolean koth = g.getEventId() == kothStorer.getEventId(g.getGame());
                boolean tourney = !koth && MobileJsonHelper.isTournamentGame(g.getEventId(), currentTournies);
                String ratedStr = MobileJsonHelper.ratedStr(koth, tourney, g.isRated());

                String color = invitationColor(s, myPID);
                DSGPlayerData d = playerStorer.loadPlayer(s.getInviterPid());
                DSGPlayerGameData gd = d.getPlayerGameData(s.getGame1().getGame());
                result.add(new InvitationEntry(
                        s.getSetId(),
                        GridStateFactory.getGameName(s.getGame1().getGame()),
                        d.getName(),
                        MobileJsonHelper.playerRating(gd),
                        color,
                        s.getGame1().getDaysPerMove(),
                        ratedStr,
                        MobileJsonHelper.playerColorNonZero(d),
                        d.getTourneyWinner()
                ));
            }
            return result;
        }

        private List<InvitationEntry> buildInvitesSent(long myPID) throws DSGPlayerStoreException {
            List<InvitationEntry> result = new ArrayList<>();
            for (TBSet s : invitesFrom) {
                TBGame g = s.getGame1();
                boolean koth = g.getEventId() == kothStorer.getEventId(g.getGame());
                boolean tourney = !koth && MobileJsonHelper.isTournamentGame(g.getEventId(), currentTournies);
                String ratedStr = MobileJsonHelper.ratedStr(koth, tourney, g.isRated());
                if (s.getInvitationRestriction() == TBSet.BEGINNER) {
                    ratedStr += ", beginner";
                }

                String color = invitationColor(s, myPID);
                long pid = s.getInviteePid();
                String opponentName;
                int opponentRating;
                int opponentColor;
                int opponentTourneyWinner;

                if (pid != 0) {
                    DSGPlayerData d = playerStorer.loadPlayer(pid);
                    DSGPlayerGameData gd = d.getPlayerGameData(s.getGame1().getGame());
                    opponentName = d.getName();
                    opponentRating = MobileJsonHelper.playerRating(gd);
                    opponentColor = MobileJsonHelper.playerColorNonZero(d);
                    opponentTourneyWinner = d.getTourneyWinner();
                } else {
                    opponentName = openInvitationLabel(s);
                    opponentRating = 1600;
                    opponentColor = 0;
                    opponentTourneyWinner = 0;
                }

                result.add(new InvitationEntry(
                        s.getSetId(),
                        GridStateFactory.getGameName(s.getGame1().getGame()),
                        opponentName, opponentRating, color,
                        s.getGame1().getDaysPerMove(), ratedStr,
                        opponentColor, opponentTourneyWinner
                ));
            }
            return result;
        }

        private List<GameEntry> buildGameEntries(List<TBGame> games, long myPID) throws DSGPlayerStoreException {
            List<GameEntry> result = new ArrayList<>();
            for (TBGame g : games) {
                boolean koth = g.getEventId() == kothStorer.getEventId(g.getGame());
                boolean tourney = !koth && MobileJsonHelper.isTournamentGame(g.getEventId(), currentTournies);
                String ratedStr = MobileJsonHelper.ratedStr(koth, tourney, g.isRated());
                String color = MobileJsonHelper.gameColor(g, myPID);
                long oppPid = myPID == g.getPlayer1Pid() ? g.getPlayer2Pid() : g.getPlayer1Pid();
                DSGPlayerData d = playerStorer.loadPlayer(oppPid);
                DSGPlayerGameData gd = d.getPlayerGameData(g.getGame());
                result.add(new GameEntry(
                        g.getGid(),
                        GridStateFactory.getGameName(g.getGame()),
                        d.getName(),
                        MobileJsonHelper.playerRating(gd),
                        color,
                        g.getNumMoves() + 1,
                        Utilities.getTimeLeft(g.getTimeoutDate().getTime()),
                        ratedStr,
                        MobileJsonHelper.playerColorNonZero(d),
                        d.getTourneyWinner()
                ));
            }
            return result;
        }

        private List<OpenInvitationEntry> buildOpenInvitations() throws DSGPlayerStoreException {
            List<OpenInvitationEntry> result = new ArrayList<>();
            for (TBSet s : waitingSets) {
                boolean go = MobileJsonHelper.isGoGame(s.getGame1().getGame());
                String color;
                if (s.isTwoGameSet()) {
                    color = "whiteblack";
                } else if (s.getPlayer2Pid() == 0) {
                    color = go ? "white (p2)" : "black (p2)";
                } else {
                    color = !go ? "white (p1)" : "black (p1)";
                }
                boolean koth = kothStorer.getEventId(s.getGame1().getGame())
                        == s.getGame1().getEventId();
                String ratedStr = MobileJsonHelper.ratedStr(koth, false, s.getGame1().isRated());
                if (s.getInvitationRestriction() == TBSet.BEGINNER) {
                    ratedStr += ", beginner";
                }
                DSGPlayerData d = playerStorer.loadPlayer(s.getInviterPid());
                DSGPlayerGameData gd = d.getPlayerGameData(s.getGame1().getGame());
                result.add(new OpenInvitationEntry(
                        s.getSetId(),
                        GridStateFactory.getGameName(s.getGame1().getGame()),
                        d.getName(),
                        MobileJsonHelper.playerRating(gd),
                        color,
                        s.getGame1().getDaysPerMove(),
                        ratedStr,
                        MobileJsonHelper.playerColorNonZero(d),
                        d.getTourneyWinner()
                ));
            }
            return result;
        }

        private List<MessageEntry> buildMessages(DateFormat messageDateFormat) throws DSGPlayerStoreException {
            List<MessageEntry> result = new ArrayList<>();
            int count = 0;
            for (DSGMessage m : messages) {
                if (++count > 50) break;
                DSGPlayerData from = playerStorer.loadPlayer(m.getFromPid());
                result.add(new MessageEntry(
                        m.getMid(),
                        m.isRead(),
                        m.getSubject(),
                        from.getName(),
                        messageDateFormat.format(m.getCreationDate()),
                        MobileJsonHelper.playerColorNonZero(from),
                        from.getTourneyWinner()
                ));
            }
            return result;
        }

        private List<TournamentEntry> buildTournaments(DateFormat dateFormat) throws Throwable {
            List<TournamentEntry> result = new ArrayList<>();
            for (Tourney tmp : upcomingTournies) {
                Tourney t = tourneyStorer.getTourney(tmp.getEventID());
                result.add(new TournamentEntry(
                        t.getName(), t.getEventID(), t.getNumRounds(),
                        (t.isTurnBased() ? "tb-" : "") + GridStateFactory.getGameName(t.getGame()),
                        1,
                        dateFormat.format(t.getSignupEndDate())
                ));
            }
            for (Tourney tmp : currentTournies) {
                Tourney t = tourneyStorer.getTourney(tmp.getEventID());
                result.add(new TournamentEntry(
                        t.getName(), t.getEventID(), t.getNumRounds(),
                        (t.isTurnBased() ? "tb-" : "") + GridStateFactory.getGameName(t.getGame()),
                        t.getNumRounds() == 0 ? 2 : 3,
                        dateFormat.format(t.getStartDate())
                ));
            }
            return result;
        }

        // ── helpers ────────────────────────────────────────────────────────────

        private static String invitationColor(TBSet s, long myPID) {
            if (s.isTwoGameSet()) return "whiteblack";
            boolean go = MobileJsonHelper.isGoGame(s.getGame1().getGame());
            if (s.getPlayer2Pid() == myPID) {
                return go ? "white (p2)" : "black (p2)";
            } else {
                return !go ? "white (p1)" : "black (p1)";
            }
        }

        /**
         * Produces the "Anyone (...)" label for open (invitee = 0) sent invitations.
         */
        private String openInvitationLabel(TBSet s) {
            String label = "Anyone";
            DSGPlayerGameData myGd = null;
            int myRating = 1600;
            if (s.getInvitationRestriction() != TBSet.ANY_RATING) {
                myGd = playerData.getPlayerGameData(s.getGame1().getGame());
                if (myGd != null && myGd.getTotalGames() > 0) {
                    myRating = (int) Math.round(myGd.getRating());
                }
            }
            char restriction = s.getInvitationRestriction();
            if (restriction == TBSet.ANYONE_NOTPLAYING) label += " (new opponent)";
            else if (restriction == TBSet.LOWER_RATING) label += " under " + myRating;
            else if (restriction == TBSet.HIGHER_RATING) label += " over " + myRating;
            else if (restriction == TBSet.SIMILAR_RATING) label += " similar";
            else if (restriction == TBSet.CLASS_RATING) {
                if (myRating >= 1900) label += " red";
                else if (myRating >= 1700) label += " yellow";
                else if (myRating >= 1400) label += " blue";
                else if (myRating >= 1000) label += " green";
                else label += " gray";
            }
            return label;
        }
    }
}