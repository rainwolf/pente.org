package org.pente.gameServer.tourney;

import org.apache.log4j.Category;
import org.pente.game.GridStateFactory;
import org.pente.gameServer.core.CacheDSGPlayerStorer;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerGameData;
import org.pente.gameServer.server.Resources;
import org.pente.kingOfTheHill.CacheKOTHStorer;
import org.pente.kingOfTheHill.KOTHStorer;
import org.pente.notifications.NotificationServer;
import org.pente.gameServer.server.RedisConnectionManager;
import org.pente.turnBased.CacheTBStorer;

import java.util.*;

public class CacheTourneyStorer implements TourneyStorer {

    private static final Category log4j = Category.getInstance(
            CacheTourneyStorer.class.getName());

    private TourneyStorer backingStorer;
    private List<TourneyListener> listeners = new ArrayList<TourneyListener>();

    private CacheTBStorer tbStorer;
    private CacheDSGPlayerStorer dsgPlayerStorer;
    private NotificationServer notificationServer;
    private KOTHStorer kothStorer;

    private List<Timer> timers = null;

    private final RedisConnectionManager pente_cache = RedisConnectionManager.getInstance();

    private static final String LIST_FIELD = "list";

    /** THE write primitive: persist a tourney as the single source of truth. */
    private void persistTourney(Tourney t) {
        if (t == null) return;
        pente_cache.hput(RedisConnectionManager.EID_TO_TOURNEY, t.getEventID(), t);
    }

    /** Read an ordered eid list stored under a single fixed field in a namespace. */
    @SuppressWarnings("unchecked")
    private java.util.List<Integer> readEidList(String namespace) {
        java.util.ArrayList<Integer> l = pente_cache.hget(namespace, LIST_FIELD);
        return l == null ? new java.util.ArrayList<Integer>() : l;
    }

    /** Write an ordered eid list under a single fixed field in a namespace. */
    private void writeEidList(String namespace, java.util.List<Integer> eids) {
        pente_cache.hput(namespace, LIST_FIELD, new java.util.ArrayList<Integer>(eids));
    }

    /** Move an eid from one list namespace to another (dedup). */
    private void moveEid(String fromNs, String toNs, int eid) {
        java.util.List<Integer> from = readEidList(fromNs);
        from.remove(Integer.valueOf(eid));
        writeEidList(fromNs, from);
        java.util.List<Integer> to = readEidList(toNs);
        if (!to.contains(eid)) { to.add(eid); writeEidList(toNs, to); }
    }


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
        // Each invalidate() drops the list's in-hash loaded marker with its
        // data, so the next getter re-bootstraps from the DB.
        pente_cache.invalidate(RedisConnectionManager.EID_TO_TOURNEY);
        pente_cache.invalidate(RedisConnectionManager.EID_TO_TOURNEY_PLAYER_PIDS);
        pente_cache.invalidate(RedisConnectionManager.TOURNEY_LIST_UPCOMING);
        pente_cache.invalidate(RedisConnectionManager.TOURNEY_LIST_CURRENT);
        pente_cache.invalidate(RedisConnectionManager.TOURNEY_LIST_COMPLETED);
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
        persistTourney(tourney);
        java.util.List<Integer> up = readEidList(RedisConnectionManager.TOURNEY_LIST_UPCOMING);
        if (!up.contains(tourney.getEventID())) { up.add(tourney.getEventID()); writeEidList(RedisConnectionManager.TOURNEY_LIST_UPCOMING, up); }
    }

    /**
     * "List bootstrapped from DB" sentinels live in Redis (not JVM fields) so
     * that losing the cached data — crash without save, FLUSHALL, failover —
     * also loses the sentinel and the next read re-bootstraps from the DB.
     */
    private boolean listLoaded(String listNamespace) {
        return pente_cache.isLoaded(listNamespace);
    }

    private void markListLoaded(String listNamespace) {
        pente_cache.markLoaded(listNamespace);
    }

    public synchronized List<Tourney> getUpcomingTournies() throws Throwable {
        // Redis down: an empty cached eid list is indistinguishable from a
        // down cache, so serve straight from the DB (the source of truth)
        // rather than bootstrap-then-read-back an empty list.
        if (!pente_cache.isAvailable()) {
            return backingStorer.getUpcomingTournies();
        }
        if (!listLoaded(RedisConnectionManager.TOURNEY_LIST_UPCOMING)) {
            java.util.List<Tourney> backing = backingStorer.getUpcomingTournies();
            java.util.List<Integer> bootstrapEids = new java.util.ArrayList<Integer>();
            for (Tourney bt : backing) { if (!bootstrapEids.contains(bt.getEventID())) bootstrapEids.add(bt.getEventID()); }
            writeEidList(RedisConnectionManager.TOURNEY_LIST_UPCOMING, bootstrapEids);
            markListLoaded(RedisConnectionManager.TOURNEY_LIST_UPCOMING);
        }
        java.util.List<Integer> eids = readEidList(RedisConnectionManager.TOURNEY_LIST_UPCOMING);
        java.util.List<Tourney> out = new java.util.ArrayList<Tourney>();
        java.util.List<Integer> promote = new java.util.ArrayList<Integer>();
        Date today = new Date();
        for (Integer eid : new java.util.ArrayList<Integer>(eids)) {
            Tourney t = getTourney(eid);
            if (t == null) continue;
            if (t.getSignupEndDate().before(today)) { promote.add(eid); }
            else { out.add(t); }
        }
        if (!promote.isEmpty()) {
            java.util.List<Integer> freshUp = readEidList(RedisConnectionManager.TOURNEY_LIST_UPCOMING);
            freshUp.removeAll(promote);
            writeEidList(RedisConnectionManager.TOURNEY_LIST_UPCOMING, freshUp);
            java.util.List<Integer> cur = readEidList(RedisConnectionManager.TOURNEY_LIST_CURRENT);
            for (Integer eid : promote) if (!cur.contains(eid)) cur.add(eid);
            writeEidList(RedisConnectionManager.TOURNEY_LIST_CURRENT, cur);
        }
        return out;
    }


    public synchronized List<Tourney> getCurrentTournies() throws Throwable {
        if (!pente_cache.isAvailable()) {
            return backingStorer.getCurrentTournies();
        }
        if (!listLoaded(RedisConnectionManager.TOURNEY_LIST_CURRENT)) {
            java.util.List<Tourney> backing = backingStorer.getCurrentTournies();
            java.util.List<Integer> bootstrapEids = new java.util.ArrayList<Integer>();
            for (Tourney bt : backing) { if (!bootstrapEids.contains(bt.getEventID())) bootstrapEids.add(bt.getEventID()); }
            writeEidList(RedisConnectionManager.TOURNEY_LIST_CURRENT, bootstrapEids);
            markListLoaded(RedisConnectionManager.TOURNEY_LIST_CURRENT);
        }
        java.util.List<Integer> eids = readEidList(RedisConnectionManager.TOURNEY_LIST_CURRENT);
        java.util.List<Tourney> out = new java.util.ArrayList<Tourney>();
        java.util.List<Integer> ended = new java.util.ArrayList<Integer>();
        Date today = new Date();
        for (Integer eid : new java.util.ArrayList<Integer>(eids)) {
            Tourney t = getTourney(eid);
            if (t == null) continue;
            if (t.getNumRounds() > 0) {
                checkRoundStatus(t);
            }
            if (t.getEndDate() != null && t.getEndDate().before(today)) {
                ended.add(eid);
            } else {
                out.add(t);
            }
        }
        if (!ended.isEmpty()) {
            // Re-read CURRENT fresh: checkRoundStatus()->completeTourney() may have
            // already moved completed eids out of CURRENT during the loop. Writing back
            // the stale loop-start snapshot would re-insert them.
            java.util.List<Integer> fresh = readEidList(RedisConnectionManager.TOURNEY_LIST_CURRENT);
            fresh.removeAll(ended);
            writeEidList(RedisConnectionManager.TOURNEY_LIST_CURRENT, fresh);
        }
        return out;
    }

    public synchronized List<Tourney> getCompletedTournies() throws Throwable {
        if (!pente_cache.isAvailable()) {
            return backingStorer.getCompletedTournies();
        }
        if (!listLoaded(RedisConnectionManager.TOURNEY_LIST_COMPLETED)) {
            java.util.List<Tourney> backing = backingStorer.getCompletedTournies();
            java.util.List<Integer> bootstrapEids = new java.util.ArrayList<Integer>();
            for (Tourney bt : backing) { if (!bootstrapEids.contains(bt.getEventID())) bootstrapEids.add(bt.getEventID()); }
            writeEidList(RedisConnectionManager.TOURNEY_LIST_COMPLETED, bootstrapEids);
            markListLoaded(RedisConnectionManager.TOURNEY_LIST_COMPLETED);
        }
        java.util.List<Integer> eids = readEidList(RedisConnectionManager.TOURNEY_LIST_COMPLETED);
        java.util.List<Tourney> out = new java.util.ArrayList<Tourney>();
        for (Integer eid : eids) {
            Tourney t = getTourney(eid);
            if (t != null) out.add(t);
        }
        return out;
    }

    public synchronized void completeTourney(Tourney tourney) throws Throwable {
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

        persistTourney(tourney);
        moveEid(RedisConnectionManager.TOURNEY_LIST_CURRENT,
                RedisConnectionManager.TOURNEY_LIST_COMPLETED, tourney.getEventID());

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
        // Redis (EID_TO_TOURNEY) is the source of truth; fall through to backing.
        log4j.debug("getTourney(" + eid + ")");
        Tourney t = pente_cache.hget(RedisConnectionManager.EID_TO_TOURNEY, eid);
        if (t == null) {
            t = backingStorer.getTourney(eid);
            if (t != null) persistTourney(t);
        }
        return t;
    }

    public synchronized void addPlayerToTourney(long pid, int eid) throws Throwable {
        log4j.debug("addPlayerToTourney(" + pid + ", " + eid + ")");

        // don't update cache since pull current ratings with each query
        // could make cached by caching pid's only, then pulling
        // ratings from cacheplayerstorer
        backingStorer.addPlayerToTourney(pid, eid);

        java.util.List<Long> pids = getTourneyPlayerPids(eid);   // loads+caches current
        if (!pids.contains(pid)) pids.add(pid);
        pente_cache.hput(RedisConnectionManager.EID_TO_TOURNEY_PLAYER_PIDS, eid, new java.util.ArrayList<Long>(pids));

        notifyListeners(new TourneyEvent(eid, TourneyEvent.PLAYER_REGISTER,
                Long.valueOf(pid)));
    }

    public synchronized void removePlayerFromTourney(long pid, int eid) throws Throwable {
        log4j.debug("removePlayerFromTourney(" + pid + ", " + eid + ")");
        backingStorer.removePlayerFromTourney(pid, eid);

        java.util.List<Long> pids = getTourneyPlayerPids(eid);
        pids.remove(Long.valueOf(pid));   // remove the Long value, NOT remove-by-index
        pente_cache.hput(RedisConnectionManager.EID_TO_TOURNEY_PLAYER_PIDS, eid, new java.util.ArrayList<Long>(pids));

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
        java.util.ArrayList<Long> pids = pente_cache.hget(RedisConnectionManager.EID_TO_TOURNEY_PLAYER_PIDS, eid);
        if (pids == null) {
            List<Long> loaded = backingStorer.getTourneyPlayerPids(eid);
            pids = new java.util.ArrayList<Long>(loaded);
            pente_cache.hput(RedisConnectionManager.EID_TO_TOURNEY_PLAYER_PIDS, eid, pids);
        }
        return new java.util.ArrayList<Long>(pids);
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

        // persist the owning tourney so the new round (added to it via
        // createFirstRound/createNextRound -> addRound(this)) reaches Redis.
        // The manageTourney.jsp path calls insertRound directly without a prior
        // persistTourney; the startTournament/checkRoundStatus paths already
        // persisted, so this redundant persist is harmless (idempotent).
        if (round.getTourney() != null) {
            persistTourney(round.getTourney());
        }
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
                GridStateFactory.isSingleGameSet(t.getGame())
        )) {
            this.tbStorer.createTournamentSet(t.getGame(), tourneyMatch.getPlayer1().getPlayerID(), tourneyMatch.getPlayer2().getPlayerID(),
                    t.getInitialTime(), t.getEventID());
        }
    }


    /**
     * Re-find the canonical match (by match id) inside a freshly-loaded section.
     * After the Redis migration getTourney/getUnplayedMatch return DETACHED
     * deserialized copies, so callers' match objects are no longer the same
     * instances held by the cached aggregate; we must re-find by id.
     */
    private static TourneyMatch findMatch(TourneySection s, TourneyMatch like) {
        for (TourneyMatch m : s.getMatches()) {
            if (m.getMatchID() == like.getMatchID()) {
                return m;
            }
        }
        return null;
    }

    /**
     * Apply a (possibly detached) match's mutable result fields onto the
     * canonical match inside the supplied tourney graph, re-init the section,
     * and run any single-elimination tie follow-up. Does NOT persist; the caller
     * persists the graph (so caller-passed tourneys stay the source of truth).
     */
    private void applyMatchTo(Tourney t, TourneyMatch tourneyMatch) throws Throwable {
        TourneySection s = t.getRound(tourneyMatch.getRound()).getSection(tourneyMatch.getSection());

        TourneyMatch canonical = findMatch(s, tourneyMatch);
        if (canonical != null) {
            canonical.setGid(tourneyMatch.getGid());
            canonical.setResult(tourneyMatch.getResult());
            canonical.setForfeit(tourneyMatch.isForfeit());
        }

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
                if (!GridStateFactory.isSingleGameSet(t.getGame())) {
                    insertMatch(more[1]);
                    s.addMatch(more[1]);
                }
            }
        }
    }

    /**
     * update a group of matches and then check if round needs to be updated
     * right now only called from admin management screen
     *
     * Operates on the CALLER'S passed tourney (so admin/manageTourney.jsp's
     * reads-after-write see the applied results) and persists it.
     */
    public synchronized void updateMatches(List tourneyMatches, Tourney t) throws Throwable {

        log4j.debug("updateMatches()");
        if (tourneyMatches != null) {
            for (Iterator it = tourneyMatches.iterator(); it.hasNext(); ) {
                TourneyMatch m = (TourneyMatch) it.next();
                backingStorer.updateMatch(m);
                applyMatchTo(t, m);
            }
        }
        persistTourney(t);
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

        // load the canonical (cached) tourney, re-find + apply, then persist.
        // tourneyMatch is a DETACHED copy, so we cannot mutate it in place.
        Tourney t = getTourney(tourneyMatch.getEvent());
        applyMatchTo(t, tourneyMatch);
        persistTourney(t);
    }

    private void checkRoundStatus(Tourney t) throws Throwable {

        if (t.isComplete()) {
            completeTourney(t);
            notificationServer.sendAdminNotification(t.getName() + " completed. Winner is " + t.getWinner());
        } else if (t.getLastRound().isComplete()) {
            TourneyRound newRound = t.createNextRound(dsgPlayerStorer);
            // persist the new round into the aggregate BEFORE insertRound, so
            // insertRound's getTourney(...) re-reads the round we just created.
            persistTourney(t);
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
                tournament.setStatus('S');
                persistTourney(tournament);   // persist the status change
                cancelTourney(tourneyID);
            } else {
                log4j.info("Starting tournament " + tournament.getName() +
                        " with " + players.size() + " players.");
                TourneyRound newRound = tournament.createFirstRound(players);
                // persist the first round into the aggregate BEFORE insertRound,
                // so insertRound's getTourney(...) re-reads the round.
                persistTourney(tournament);
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

    private synchronized void startAnotherTourney(int eid) throws Throwable {
        log4j.info("startAnotherTourney from (" + eid + ")");
        Tourney tourney = getTourney(eid);
        Tourney penteAmateursTourney = null;
        String tournamentBaseName = null;
        String amateursBaseName = null;

        long time = (new Date()).getTime() + (tourney.getStatus() == 'S' ? 31L : 21L) * 24L * 3600L * 1000L;
        Date nowPlus21or31Days = new Date(time);
        Calendar cal = Calendar.getInstance();
        cal.setTime(nowPlus21or31Days);
        Date signupEndDate = new Date(time - 3600L * 1000L);

        int game = tourney.getGame();
        if (game > 50) {
            String dateSuffix = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) +
                    " " + cal.get(Calendar.YEAR);
            if (game == GridStateFactory.TB_PENTE) {
                // getCurrentTournies()/getCompletedTournies() only populate eid+name;
                // hydrate full details so game/restrictions are available below.
                List<Tourney> currentDetails = new ArrayList<>();
                for (Tourney t : getCurrentTournies()) {
                    currentDetails.add(getTourney(t.getEventID()));
                }
                List<Tourney> completedDetails = new ArrayList<>();
                for (Tourney t : getCompletedTournies()) { // sorted already
                    completedDetails.add(getTourney(t.getEventID()));
                }
                if (tourney.getRestrictions().stream().anyMatch(restriction ->
                        (restriction.getType() == Restriction.RATING_RESTRICTION_ABOVE) ||
                                (restriction.getType() == Restriction.RATING_RESTRICTION_BELOW))) {
                    if (currentDetails.stream().anyMatch(t ->
                            (t.getGame() == GridStateFactory.TB_PENTE) &&
                                    t.getRestrictions().stream().anyMatch(restriction ->
                                            (restriction.getType() == Restriction.RATING_RESTRICTION_ABOVE) ||
                                                    (restriction.getType() == Restriction.RATING_RESTRICTION_BELOW))
                                    && !t.isComplete())) {
                        log4j.info("Pente Masters or Amateurs still ongoing, not starting Pente Open");
                        return;
                    }  // if no masters/amateurs ongoing, start open next
                    tournamentBaseName = "Pente Open " + dateSuffix;
                    tourney = null;
                    for (Tourney t : completedDetails) { // sorted already
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
                    tournamentBaseName = "Pente Masters " + dateSuffix;
                    tourney = null;
                    for (Tourney t : completedDetails) { // sorted already
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

                    amateursBaseName = "Pente Amateurs " + dateSuffix;
                    for (Tourney t : completedDetails) { // sorted already
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
                tournamentBaseName = GridStateFactory.getDisplayName(game - 50) + " " + dateSuffix;
            }
        }
        String newName = findNextTournamentName(tournamentBaseName);
        tourney.setName(newName);
        tourney.setStartDate(nowPlus21or31Days);
        tourney.setSignupEndDate(signupEndDate);
        insertTourney(tourney);
        if (amateursBaseName != null) { // we're starting Pente Masters/Amateurs
            newName = findNextTournamentName(amateursBaseName);
            penteAmateursTourney.setName(newName);
            penteAmateursTourney.setStartDate(nowPlus21or31Days);
            penteAmateursTourney.setSignupEndDate(signupEndDate);
            insertTourney(penteAmateursTourney);
        }
        flushCache();
    }

    public synchronized void cancelTourney(int eid) throws Throwable {
        log4j.info("cancelTourney(" + eid + ")");
        backingStorer.cancelTourney(eid);

        // remove from all three lists + drop the tourney + its pid index
        for (String ns : new String[]{RedisConnectionManager.TOURNEY_LIST_UPCOMING, RedisConnectionManager.TOURNEY_LIST_CURRENT, RedisConnectionManager.TOURNEY_LIST_COMPLETED}) {
            java.util.List<Integer> l = readEidList(ns);
            if (l.remove(Integer.valueOf(eid))) writeEidList(ns, l);
        }
        pente_cache.hremove(RedisConnectionManager.EID_TO_TOURNEY, eid);
        pente_cache.hremove(RedisConnectionManager.EID_TO_TOURNEY_PLAYER_PIDS, eid);

        startAnotherTourney(eid);
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
