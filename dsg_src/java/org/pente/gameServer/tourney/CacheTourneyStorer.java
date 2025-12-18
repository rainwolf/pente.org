package org.pente.gameServer.tourney;

import java.util.*;

import org.apache.log4j.*;

import org.pente.game.*;
import org.pente.gameServer.server.Resources;
import org.pente.kingOfTheHill.CacheKOTHStorer;
import org.pente.kingOfTheHill.KOTHStorer;
import org.pente.notifications.NotificationServer;
import org.pente.turnBased.CacheTBStorer;
import org.pente.gameServer.core.*;

public class CacheTourneyStorer implements TourneyStorer {

    private static final Category log4j = Category.getInstance(
            CacheTourneyStorer.class.getName());

    private TourneyStorer backingStorer;
    private Map<Integer, Tourney> tournies = new HashMap<Integer, Tourney>();
    private List<Tourney> upcomingTournies = null;
    private List<Tourney> currentTournies = null;
    private List<Tourney> completedTournies = null;
    private Map<Integer, List<Long>> tourneyPlayerPids = null;
    private List<TourneyListener> listeners = new ArrayList<TourneyListener>();

    private CacheTBStorer tbStorer;
    private CacheDSGPlayerStorer dsgPlayerStorer;
    private NotificationServer notificationServer;
    private KOTHStorer kothStorer;

    private List<Timer> timers = null;


    public void setDsgPlayerStorer(CacheDSGPlayerStorer dsgPlayerStorer) {
        this.dsgPlayerStorer = dsgPlayerStorer;
    }

    public void setTBStorer(CacheTBStorer tbStorer) {
        this.tbStorer = tbStorer;
    }

    public void setNotificationServer(NotificationServer notificationServer) {
        this.notificationServer = notificationServer;
    }

    public void setKothStorer(KOTHStorer kothStorer) {
        this.kothStorer = kothStorer;
    }

    public CacheTourneyStorer(TourneyStorer backingStorer) {
        this.backingStorer = backingStorer;
        this.timers = new ArrayList<>();
    }


    public void addTourneyListener(TourneyListener listener) {
        listeners.add(listener);
    }

    public void removeTourneyListener(TourneyListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(TourneyEvent event) {
        for (Iterator it = listeners.iterator(); it.hasNext(); ) {
            TourneyListener tl = (TourneyListener) it.next();
            tl.tourneyEventOccurred(event);
        }
    }

    public synchronized void flushCache() {
        tournies.clear();
        upcomingTournies = null;
        currentTournies = null;
        completedTournies = null;
        tourneyPlayerPids = null;
    }

    public synchronized void insertTourney(Tourney tourney, Resources resources) throws Throwable {
        insertTourney(tourney);
        if (tourney.isSpeed()) {
            Date oneHourAgo = new Date();
            Date now = new Date();
            oneHourAgo.setTime(oneHourAgo.getTime() - 3600L * 1000);
            if (tourney.getStartDate().before(oneHourAgo)) {
                resources.startNewServer(tourney.getEventID());
            } else {
                Date startDate = new Date(tourney.getStartDate().getTime() - 3600L * 1000);
                Timer timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        resources.startNewServer(tourney.getEventID());
                        timer.cancel();
                        timer.purge();
                    }
                }, startDate);
            }
            if (tourney.getNumRounds() == 0) {
                startTournamentOrSetupTimer(tourney);
            }
        } else if (tourney.isTurnBased() && tourney.getNumRounds() == 0) {
            startTournamentOrSetupTimer(tourney);
        }
    }

    public synchronized void insertTourney(Tourney tourney) throws Throwable {
        backingStorer.insertTourney(tourney);

        log4j.debug("insertTourney(" + tourney.getEventID() + "), cached");
        tournies.put(tourney.getEventID(), tourney);
        upcomingTournies = null;
    }

    public List<Tourney> getUpcomingTournies() throws Throwable {
        // more complicated to cache, not that important anyways
        if (upcomingTournies == null) {
            upcomingTournies = backingStorer.getUpcomingTournies();
        } else {
            Date today = new Date();
            for (Iterator<Tourney> iterator = upcomingTournies.iterator(); iterator.hasNext(); ) {
                Tourney t = iterator.next();
                Tourney fullTourney = getTourney(t.getEventID());
                if (fullTourney.getSignupEndDate().before(today)) {
                    currentTournies.add(t);
                    iterator.remove();
                }
            }
        }
        return upcomingTournies;
    }


    public List<Tourney> getCurrentTournies() throws Throwable {
        // more complicated to cache, not that important anyways
        if (currentTournies == null) {
            currentTournies = backingStorer.getCurrentTournies();
        } else {
            Date today = new Date();
            for (Iterator<Tourney> iterator = currentTournies.iterator(); iterator.hasNext(); ) {
                Tourney t = iterator.next();
                Tourney fullTourney = getTourney(t.getEventID());
                if (fullTourney.getNumRounds() > 0) {
                    checkRoundStatus(fullTourney);
                }
                if (fullTourney.getEndDate() != null && fullTourney.getEndDate().before(today)) {
                    iterator.remove();
                }
            }
        }
        return currentTournies;
    }

    public List<Tourney> getCompletedTournies() throws Throwable {
        // more complicated to cache, not that important anyways
        if (completedTournies == null) {
            completedTournies = backingStorer.getCompletedTournies();
        }
        return completedTournies;
    }

    public void completeTourney(Tourney tourney) throws Throwable {
        log4j.debug("completeTourney(" + tourney.getEventID() + ")");

        tourney.setEndDate(new Date());
        backingStorer.completeTourney(tourney);

        // notify listeners that it's complete
        // used for speed-tournies to notify main room
        notifyListeners(new TourneyEvent(tourney.getEventID(),
                TourneyEvent.COMPLETE));

        List<Tourney> completedDetails = new ArrayList<>();
        for (Tourney d : getCompletedTournies()) {
            completedDetails.add(getTourney(d.getEventID()));
        }
        Collections.sort(completedDetails, (o1, o2) -> o2.getStartDate().compareTo(o1.getStartDate()));
        Tourney lastTourney = null;
        int currentCrownInt = getCrownInt(tourney.getPrize());
        for (Tourney t : completedDetails) {
            if (t.getGame() == tourney.getGame() && currentCrownInt == getCrownInt(t.getPrize())
                    && compareRestrictions(t.getEventID(), tourney.getEventID())) {
                lastTourney = t;
                break;
            }
        }

        if (lastTourney != null) {
            backingStorer.removeCrown(lastTourney.getEventID(), lastTourney.getGame(), lastTourney.getWinnerPid(), currentCrownInt);
            ((CacheKOTHStorer) kothStorer).adjustCrown(lastTourney.getGame());
            dsgPlayerStorer.refreshPlayer(lastTourney.getWinner());
            backingStorer.assignCrown(tourney.getEventID(), tourney.getGame(), tourney.getWinnerPid(), currentCrownInt);
            dsgPlayerStorer.refreshPlayer(tourney.getWinner());
        }

        completedTournies = null;
        currentTournies = null;

        if (tourney.isTurnBased()) {
            startAnotherTourney(tourney.getEventID());
        }
    }

    private boolean compareRestrictions(int eid1, int eid2) throws Throwable {
        Tourney t1 = getTourney(eid1), t2 = getTourney(eid2);
        List<Restriction> t1Restrictions = t1.getRestrictions(), t2Restrictions = t2.getRestrictions();
        if (t1Restrictions == null || t2Restrictions == null) {
            return t1Restrictions == t2Restrictions;
        }
        for (Restriction r : t1Restrictions) {
            if (!t2Restrictions.contains(r)) {
                return false;
            }
        }
        for (Restriction r : t2Restrictions) {
            if (!t1Restrictions.contains(r)) {
                return false;
            }
        }
        return true;
    }


    public synchronized Tourney getTourney(int eid) throws Throwable {
        // cache tourney data, including round/section/match data

        log4j.debug("getTourney(" + eid + ")");
        Tourney t = (Tourney) tournies.get(Integer.valueOf(eid));

        if (t != null) {
            log4j.debug("return cached copy");
        } else {
            t = backingStorer.getTourney(eid);
            tournies.put(Integer.valueOf(eid), t);
        }

        return t;
    }

    public synchronized void addPlayerToTourney(long pid, int eid) throws Throwable {
        log4j.debug("addPlayerToTourney(" + pid + ", " + eid + ")");

        // don't update cache since pull current ratings with each query
        // could make cached by caching pid's only, then pulling
        // ratings from cacheplayerstorer
        backingStorer.addPlayerToTourney(pid, eid);

        if (tourneyPlayerPids != null) {
            List<Long> playerPids = tourneyPlayerPids.get(eid);
            if (playerPids != null) {
                playerPids.add(pid);
            }
        }

        notifyListeners(new TourneyEvent(eid, TourneyEvent.PLAYER_REGISTER,
                Long.valueOf(pid)));
    }

    public synchronized void removePlayerFromTourney(long pid, int eid) throws Throwable {
        log4j.debug("removePlayerFromTourney(" + pid + ", " + eid + ")");
        backingStorer.removePlayerFromTourney(pid, eid);

        if (tourneyPlayerPids != null) {
            List<Long> playerPids = tourneyPlayerPids.get(eid);
            if (playerPids != null) {
                playerPids.remove(pid);
            }
        }

        notifyListeners(new TourneyEvent(eid, TourneyEvent.PLAYER_DROP,
                Long.valueOf(pid)));
    }


    public List<TourneyPlayerData> getTourneyPlayers(int eid) throws Throwable {
        // don't cache since pull current ratings with each query
        // could make cached by caching pid's only, then pulling
        // ratings from cacheplayerstorer
        return backingStorer.getTourneyPlayers(eid);
    }

    @Override
    public synchronized List<Long> getTourneyPlayerPids(int eid) throws Throwable {
        if (tourneyPlayerPids == null) {
            tourneyPlayerPids = new HashMap<>();
        }
        List<Long> playerPids = tourneyPlayerPids.get(eid);
        if (playerPids == null) {
            playerPids = backingStorer.getTourneyPlayerPids(eid);
            tourneyPlayerPids.put(eid, playerPids);
        }

        return playerPids;
    }

    public Tourney getTourneyDetails(int eid) throws Throwable {
        log4j.debug("getTourneyDetails(" + eid + ")");
        return getTourney(eid);
    }


    public synchronized TourneyMatch getUnplayedMatch(long player1ID,
                                                      long player2ID, int eid)
            throws Throwable {

        log4j.debug("getUnplayedMatch(" + player1ID + ", " + player2ID + ", " + eid + ")");

        Tourney t = getTourney(eid);
        for (Iterator sections = t.getLastRound().getSections().iterator();
             sections.hasNext(); ) {
            TourneySection s = (TourneySection) sections.next();
            log4j.debug("checking section " + s.getSection());
            TourneyMatch m = s.getUnplayedMatch(player1ID, player2ID);
            if (m != null) {
                return m;
            }
        }

        return null;
    }

    public List<TourneyPlayerData> setInitialSeeds(int eid) throws Throwable {
        // not necessary to cache yet
        Tourney tourney = getTourneyDetails(eid);
        List<Long> playersToRemove = new ArrayList<>();
        if (!tourney.getRestrictions().isEmpty()) {
            for (Restriction restriction : tourney.getRestrictions()) {
                if (restriction.getType() == Restriction.RATING_RESTRICTION_ABOVE ||
                        restriction.getType() == Restriction.RATING_RESTRICTION_BELOW) {
                    int rating = restriction.getValue();
                    for (Long pid : getTourneyPlayerPids(eid)) {
                        DSGPlayerData dsgPlayerData = dsgPlayerStorer.loadPlayer(pid);
                        DSGPlayerGameData dsgPlayerGameData = dsgPlayerData.getPlayerGameData(tourney.getGame());
                        if (restriction.getType() == Restriction.RATING_RESTRICTION_ABOVE) {
                            if (dsgPlayerGameData.getRating() < rating) {
                                playersToRemove.add(pid);
                            }
                        } else if (restriction.getType() == Restriction.RATING_RESTRICTION_BELOW) {
                            if (dsgPlayerGameData.getRating() > rating) {
                                playersToRemove.add(pid);
                            }
                        }
                    }
                }
            }
            for (Long pid : playersToRemove) {
                removePlayerFromTourney(pid, eid);
            }
        }
        return backingStorer.setInitialSeeds(eid);
    }

    public synchronized void insertRound(TourneyRound round) throws Throwable {
        log4j.debug("insertRound(" + round.getRound() + ")");
        //backingStorer.insertRound(round, eid);

        int eid = -1;
        for (Iterator sections = round.getSections().iterator(); sections.hasNext(); ) {
            TourneySection s = (TourneySection) sections.next();
            for (Iterator matches = s.getMatches().iterator(); matches.hasNext(); ) {
                TourneyMatch m = (TourneyMatch) matches.next();
                insertMatch(m);
                eid = m.getEvent();
            }
        }


        // notify listeners of new round
        // used for speed-tournies to notify main room
        notifyListeners(new TourneyEvent(eid, TourneyEvent.NEW_ROUND));

        // don't need to cache round, should have already been done by client
    }

    public synchronized void insertMatch(TourneyMatch tourneyMatch) throws Throwable {
        log4j.debug("insertMatch(" + tourneyMatch.getMatchID() + ")");
        backingStorer.insertMatch(tourneyMatch);
        Tourney t = getTourney(tourneyMatch.getEvent());
        if (t.getGame() > 50 && tourneyMatch.getPlayer1() != null &&
                tourneyMatch.getPlayer1().getPlayerID() != 0 &&
                tourneyMatch.getPlayer2() != null &&
                tourneyMatch.getPlayer2().getPlayerID() != 0 && ((
                tourneyMatch.getPlayer1().getPlayerID() < tourneyMatch.getPlayer2().getPlayerID()) ||
                t.getGame() == GridStateFactory.TB_GO || t.getGame() == GridStateFactory.TB_GO9 ||
                t.getGame() == GridStateFactory.TB_GO13
        )) {
            this.tbStorer.createTournamentSet(t.getGame(), tourneyMatch.getPlayer1().getPlayerID(), tourneyMatch.getPlayer2().getPlayerID(),
                    t.getInitialTime(), t.getEventID());
        }
    }


    /**
     * update a group of matches and then check if round needs to be updated
     * right now only called from admin management screen
     */
    public synchronized void updateMatches(List tourneyMatches, Tourney t) throws Throwable {

        log4j.debug("updateMatches()");
        if (tourneyMatches != null) {
            for (Iterator it = tourneyMatches.iterator(); it.hasNext(); ) {
                TourneyMatch m = (TourneyMatch) it.next();
                updateMatchOnly(m);
            }
        }
        checkRoundStatus(t);
    }

    /**
     * update a single match and then check if round needs to be updated
     * right now only called from servertable
     */
    public synchronized void updateMatch(
            TourneyMatch tourneyMatch) throws Throwable {

        log4j.debug("updateMatch(" + tourneyMatch.getMatchID() + ")");

        updateMatchOnly(tourneyMatch);

        Tourney t = getTourney(tourneyMatch.getEvent());
        checkRoundStatus(t);
    }

    private void updateMatchOnly(TourneyMatch tourneyMatch) throws Throwable {
        log4j.debug("updateMatchOnly(" + tourneyMatch.getMatchID() + ")");
        backingStorer.updateMatch(tourneyMatch);

        Tourney t = getTourney(tourneyMatch.getEvent());
        TourneySection s = t.getRound(tourneyMatch.getRound()).getSection(tourneyMatch.getSection());

        // reinit section to show these results, and do anything else needed
        s.init();

        // don't really like this here, but haven't figured out where else to
        // do it

        // how do we tell tbstorer to create set?
        if (!tourneyMatch.isBye() && t.getFormat() instanceof SingleEliminationFormat) {
            log4j.debug("single elimination match, see if we need to create more matches because of tie");
            SingleEliminationSection s2 = (SingleEliminationSection) s;
            SingleEliminationFormat f = (SingleEliminationFormat)
                    t.getFormat();
            // if players have tied, need to create new matches for players
            // in this section
            SingleEliminationMatch m = s2.getSingleEliminationMatch(tourneyMatch);
            log4j.debug("get result of matches = " + m.getResult());
            if (m.getResult() == TourneyMatch.RESULT_TIE && t.getNumRounds() == tourneyMatch.getRound()) {
                TourneyMatch more[] = f.createMoreMatchesAfterTie(tourneyMatch);
                insertMatch(more[0]);
                s.addMatch(more[0]);
                if (t.getGame() != GridStateFactory.GO &&
                        t.getGame() != GridStateFactory.GO9 &&
                        t.getGame() != GridStateFactory.GO13 &&
                        t.getGame() != GridStateFactory.SPEED_GO &&
                        t.getGame() != GridStateFactory.SPEED_GO9 &&
                        t.getGame() != GridStateFactory.SPEED_GO13 &&
                        t.getGame() != GridStateFactory.TB_GO &&
                        t.getGame() != GridStateFactory.TB_GO9 &&
                        t.getGame() != GridStateFactory.TB_GO13) {
                    insertMatch(more[1]);
                    s.addMatch(more[1]);
                }
            }
        }
    }

    private void checkRoundStatus(Tourney t) throws Throwable {

        if (t.isComplete()) {
            completeTourney(t);
            notificationServer.sendAdminNotification(t.getName() + " completed. Winner is " + t.getWinner());
        } else if (t.getLastRound().isComplete()) {
            TourneyRound newRound = t.createNextRound(dsgPlayerStorer);
            insertRound(newRound);
            notificationServer.sendAdminNotification("Round " + t.getNumRounds() + " started in " + t.getName());
        }
    }

    private int getCrownInt(String prizeStr) {
        int crownInt = 0;
        prizeStr = prizeStr.toLowerCase();
        if (prizeStr.contains("gold")) {
            crownInt = DSGPlayerGameData.TOURNEY_WINNER_GOLD;
        } else if (prizeStr.contains("silver")) {
            crownInt = DSGPlayerGameData.TOURNEY_WINNER_SILVER;
        } else if (prizeStr.contains("bronze")) {
            crownInt = DSGPlayerGameData.TOURNEY_WINNER_BRONZE;
        }
        return crownInt;
    }

    @Override
    public void assignCrown(int eid, int game, long pid, int crown) throws Throwable {
        Tourney tourney = getTourney(eid);
        if (tourney != null) {
            int crownInt = getCrownInt(tourney.getPrize());
            int gameInt = tourney.getGame();
            long winner = tourney.getWinnerPid();
            backingStorer.assignCrown(eid, gameInt, winner, crownInt);
            dsgPlayerStorer.refreshPlayer(tourney.getWinner());
        }
    }

    @Override
    public void removeCrown(int eid, int game, long pid, int crown) throws Throwable {
        Tourney tourney = getTourney(eid);
        if (tourney != null) {
            String prizeStr = tourney.getPrize();
            int crownInt = getCrownInt(prizeStr);
            int gameInt = tourney.getGame();
            long winner = tourney.getWinnerPid();
            backingStorer.removeCrown(eid, gameInt, winner, crownInt);
            ((CacheKOTHStorer) kothStorer).adjustCrown(tourney.getGame());
            dsgPlayerStorer.refreshPlayer(tourney.getWinner());
        }
    }

    public void startTournament(int tourneyID) {
        try {
            // create first round
            Tourney tournament = this.getTourney(tourneyID);
            List<TourneyPlayerData> players = this.setInitialSeeds(tourneyID);
            if (players.size() < 2) {
                log4j.info("Not enough players to start tournament " + tournament.getName() +
                        ". Cancelling tournament.");
                cancelTourney(tourneyID);
            } else {
                log4j.info("Starting tournament " + tournament.getName() +
                        " with " + players.size() + " players.");
                TourneyRound newRound = tournament.createFirstRound(players);
                this.insertRound(newRound);
            }
        } catch (Throwable t) {
            t.printStackTrace();
//            log4j.error("Problem in startTournament()", t);
        }
    }

    public void startTournamentOrSetupTimer(Tourney tourney) {
        Date now = new Date();
        if (tourney.getStartDate().before(now)) {
            startTournament(tourney.getEventID());
        } else {
            Timer timer = new Timer(tourney.getName() + "_startTimer");
            int timerIdx = timers.size();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    startTournament(tourney.getEventID());
                    Timer timer = timers.get(timerIdx);
                    timer.cancel();
                    timer.purge();
                    timers.set(timerIdx, null);
                }
            }, new Date(tourney.getStartDate().getTime() + 5000L * timerIdx)); // stagger by 5 seconds per timer
            timers.add(timer);
            log4j.info("Scheduled TB tournament " + tourney.getName() +
                    " to start at " + tourney.getStartDate());
        }
    }

    public void setupTBTournaments() throws Throwable {
        List<Tourney> tournaments = new ArrayList<>();
        tournaments.addAll(this.getCurrentTournies());
        tournaments.addAll(this.getUpcomingTournies());
        for (Tourney t : tournaments) {
            Tourney tourney = this.getTourneyDetails(t.getEventID());
            if (tourney.isTurnBased() && tourney.getNumRounds() == 0) {
                log4j.info("tournament " + tourney.getName());
                startTournamentOrSetupTimer(tourney);
            }
        }
        log4j.info("Finished setting up TB Tournaments.");
    }

    public String findNextTournamentName(String baseName) throws Throwable {
        return backingStorer.findNextTournamentName(baseName);
    }

    private void startAnotherTourney(int eid) throws Throwable {
        log4j.info("startAnotherTourney from (" + eid + ")");
        Tourney tourney = getTourney(eid);
        Tourney penteAmateursTourney = null;
        String tournamentBaseName = null;
        String amateursBaseName = null;

        long time = (new Date()).getTime() + 21L * 24L * 3600L * 1000L;
        Date nowPlus21Days = new Date(time);
        Calendar cal = Calendar.getInstance();
        cal.setTime(nowPlus21Days);
        Date signupEndDate = new Date(time - 3600L * 1000L);

        int game = tourney.getGame();
        if (game > 50) {
            if (game == GridStateFactory.TB_PENTE) {
                if (tourney.getRestrictions().stream().anyMatch(restriction -> (restriction.getType() == Restriction.RATING_RESTRICTION_ABOVE) ||
                        (restriction.getType() == Restriction.RATING_RESTRICTION_BELOW))) {
                    if (getCurrentTournies().stream().anyMatch(t ->
                            (t.getGame() == GridStateFactory.TB_PENTE) &&
                                    t.getRestrictions().stream().anyMatch(restriction -> (restriction.getType() == Restriction.RATING_RESTRICTION_ABOVE) ||
                                    (restriction.getType() == Restriction.RATING_RESTRICTION_BELOW))
                            && !t.isComplete())) {
                        log4j.info("Pente Masters or Amateurs still ongoing, not starting Pente Open");
                        return;
                    }  // if no masters/amateurs ongoing, start open next
                    tournamentBaseName = "Pente Open " + cal.get(Calendar.YEAR);
                    tourney = null;
                    for (Tourney t: getCompletedTournies()) { // sorted already
                        if ((t.getGame() == GridStateFactory.TB_PENTE) &&
                            t.getRestrictions().stream().noneMatch(r ->
                                (r.getType() == Restriction.RATING_RESTRICTION_ABOVE) ||
                                (r.getType() == Restriction.RATING_RESTRICTION_BELOW))) {
                            tourney = getTourney(t.getEventID());
                            break;
                        }
                    }
                    if (tourney == null) {
                        log4j.info("No previous Pente Open tournament found, cannot start new one.");
                        return;
                    }

                } else { // open tournament just completed, start masters/amateurs next
                    tournamentBaseName = "Pente Masters " + cal.get(Calendar.YEAR);
                    tourney = null;
                    for (Tourney t: getCompletedTournies()) { // sorted already
                        if ((t.getGame() == GridStateFactory.TB_PENTE) &&
                                t.getRestrictions().stream().anyMatch(r ->
                                                (r.getType() == Restriction.RATING_RESTRICTION_BELOW))) {
                            tourney = getTourney(t.getEventID());
                            break;
                        }
                    }
                    if (tourney == null) {
                        log4j.info("No previous Pente Masters tournament found, cannot start new one.");
                        return;
                    }

                    amateursBaseName = "Pente Amateurs " + cal.get(Calendar.YEAR);
                    for (Tourney t: getCompletedTournies()) { // sorted already
                        if ((t.getGame() == GridStateFactory.TB_PENTE) &&
                                t.getRestrictions().stream().anyMatch(r ->
                                        (r.getType() == Restriction.RATING_RESTRICTION_ABOVE))) {
                            penteAmateursTourney = getTourney(t.getEventID());
                            break;
                        }
                    }

                    if (penteAmateursTourney == null) {
                        log4j.info("No previous Pente Amateurs tournament found, cannot start new one.");
                        return;
                    }
                }
            } else {
                game -= 50; // only use to retrieve the game name
                tournamentBaseName = GridStateFactory.getDisplayName(game) + " " + cal.get(Calendar.YEAR);
            }
        }
        String newName = findNextTournamentName(tournamentBaseName);
        tourney.setName(newName);
        tourney.setStartDate(nowPlus21Days);
        tourney.setSignupEndDate(signupEndDate);
        insertTourney(tourney);
        if (amateursBaseName != null) { // we're starting Pente Masters/Amateurs
            newName = findNextTournamentName(amateursBaseName);
            penteAmateursTourney.setName(newName);
            penteAmateursTourney.setStartDate(nowPlus21Days);
            penteAmateursTourney.setSignupEndDate(signupEndDate);
            insertTourney(penteAmateursTourney);
        }
    }

    public void cancelTourney(int eid) throws Throwable {
        log4j.info("cancelTourney(" + eid + ")");
        startAnotherTourney(eid);
        backingStorer.cancelTourney(eid);
        flushCache();
    }

    public void destroy() {
        for (Timer timer : timers) {
            if (timer != null) {
                timer.cancel();
                timer.purge();
            }
        }
    }
}
