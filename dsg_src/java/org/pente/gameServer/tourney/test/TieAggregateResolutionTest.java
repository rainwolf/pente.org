package org.pente.gameServer.tourney.test;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.pente.game.GridStateFactory;
import org.pente.gameServer.server.RedisConnectionManager;
import org.pente.gameServer.tourney.CacheTourneyStorer;
import org.pente.gameServer.tourney.SingleEliminationFormat;
import org.pente.gameServer.tourney.SingleEliminationMatch;
import org.pente.gameServer.tourney.SingleEliminationSection;
import org.pente.gameServer.tourney.Tourney;
import org.pente.gameServer.tourney.TourneyMatch;
import org.pente.gameServer.tourney.TourneyPlayerData;
import org.pente.gameServer.tourney.TourneyRound;
import org.pente.turnBased.CacheTBStorer;
import org.pente.turnBased.TBSet;
import org.pente.turnBased.TBStoreException;
import org.pente.turnBased.test.InMemoryDSGPlayerStorer;
import org.pente.turnBased.test.InMemoryTBGameStorer;
import org.pente.turnBased.test.SerializingRedisConnectionManager;

/**
 * End-state proof for the Renju tourney tie fix: a RESULT_TIE row plus its
 * replay row fold into one aggregate that (a) is incomplete while the replay
 * is unplayed -- the round stays open, no stall-as-unplayed -- and (b) yields
 * exactly one winner once the replay is decisive.
 *
 * The last test is an integration case over the real call site: it drives a
 * TB_RENJU single-elimination tourney through CacheTourneyStorer.updateMatch
 * and asserts the replay row that applyMatchTo's tie branch inserts, plus the
 * TB set created for it.
 *
 * Everything is built from real production objects (Tourney.createFirstRound,
 * SingleEliminationFormat, CacheTourneyStorer/CacheTBStorer over the in-memory
 * storers used by the other Redis-migration tests). Nothing is mocked.
 */
public class TieAggregateResolutionTest extends TestCase {

    private static final long ALICE = 1001L;   // seed 1
    private static final long BOB = 1002L;     // seed 2

    private CacheTourneyStorer tourneyCache;
    private CacheTBStorer tbCache;
    private List<TBSet> createdSets;

    public TieAggregateResolutionTest(String name) {
        super(name);
    }

    /**
     * Same fixture wiring as CacheTourneyStorerRedisTest / CacheTBStorerRedisTest:
     * a SerializingRedisConnectionManager (serializes on put, deserializes on
     * get, so reads are independent copies like real Redis), an
     * InMemoryTourneyStorer behind CacheTourneyStorer, and an
     * InMemoryTBGameStorer behind CacheTBStorer.
     *
     * The tbStorer wiring is load-bearing rather than incidental:
     * CacheTourneyStorer.insertMatch calls tbStorer.createTournamentSet for any
     * tourney whose game is > 50, so the replay match inserted by the tie branch
     * must produce a real TB set. createTournamentSet in turn reads
     * tourneyStorer.getTourney(eid), hence setTourneyStorer.
     */
    protected void setUp() throws Exception {
        super.setUp();
        RedisConnectionManager.setInstance(new SerializingRedisConnectionManager());
        RedisConnectionManager.getInstance().invalidate(
                RedisConnectionManager.EID_TO_TOURNEY);
        RedisConnectionManager.getInstance().invalidate(
                RedisConnectionManager.EID_TO_TOURNEY_PLAYER_PIDS);
        RedisConnectionManager.getInstance().invalidate(
                RedisConnectionManager.SID_TO_TB_SET);
        RedisConnectionManager.getInstance().invalidate(
                RedisConnectionManager.GID_TO_SID);

        createdSets = new ArrayList<TBSet>();
        // Record every set that reaches the backing store, so the replay's
        // tournament set can be inspected by pairing (InMemoryTBGameStorer keys
        // sets by the explicit setId, which createTournamentSet leaves at 0).
        InMemoryTBGameStorer tbBacking = new InMemoryTBGameStorer() {
            @Override
            public void createSet(TBSet set) throws TBStoreException {
                super.createSet(set);
                createdSets.add(set);
            }
        };

        tourneyCache = new CacheTourneyStorer(new InMemoryTourneyStorer());
        tbCache = new CacheTBStorer(
                tbBacking, new InMemoryDSGPlayerStorer(), null, null, null);
        tbCache.setTourneyStorer(tourneyCache);
        tourneyCache.setTBStorer(tbCache);
    }

    protected void tearDown() throws Exception {
        if (tbCache != null) {
            tbCache.destroy();
        }
        if (tourneyCache != null) {
            tourneyCache.destroy();
        }
        RedisConnectionManager.resetInstance();
        super.tearDown();
    }

    // ------------------------------------------------------------------
    // builders
    // ------------------------------------------------------------------

    private TourneyPlayerData player(long pid, String name, int seed) {
        TourneyPlayerData p = new TourneyPlayerData();
        p.setPlayerID(pid);
        p.setName(name);
        // production assigns seeds in setInitialSeeds(); SingleEliminationSection
        // orders rows and attributes wins by seed, so real seeds matter here.
        p.setSeed(seed);
        return p;
    }

    private List<TourneyPlayerData> twoPlayers() {
        List<TourneyPlayerData> players = new ArrayList<TourneyPlayerData>();
        players.add(player(ALICE, "Alice", 1));
        players.add(player(BOB, "Bob", 2));
        return players;
    }

    /** Single-elimination TB_RENJU (81) tourney -- a single-game-set game. */
    private Tourney renjuTourney(int eid) {
        Tourney t = new Tourney(eid);
        t.setName("Renju Tie Test " + eid);
        t.setGame(GridStateFactory.TB_RENJU);
        t.setFormat(new SingleEliminationFormat());
        t.setInitialTime(3);   // days per move, passed to createTournamentSet
        return t;
    }

    private static SingleEliminationMatch onlyAggregate(SingleEliminationSection s) {
        assertEquals("expected exactly one aggregate match for one pairing",
                1, s.getSingleEliminationMatches().size());
        return s.getSingleEliminationMatches().get(0);
    }

    // ------------------------------------------------------------------
    // row-level semantics (Task 1's mapping)
    // ------------------------------------------------------------------

    /**
     * The whole point of mapping a draw to RESULT_TIE (4) rather than leaving
     * getWinner()==0: hasBeenPlayed() is {@code result != 0}, so a drawn game
     * stored as RESULT_UNFINISHED (0) keeps its row forever unplayed and the
     * round never closes.
     */
    public void testTieRowCountsAsPlayedButUnfinishedRowDoesNot() {
        TourneyMatch tied = new TourneyMatch();
        tied.setPlayer1(player(ALICE, "Alice", 1));
        tied.setPlayer2(player(BOB, "Bob", 2));
        tied.setResult(TourneyMatch.RESULT_TIE);
        assertTrue("a RESULT_TIE row must count as played", tied.hasBeenPlayed());

        tied.setResult(TourneyMatch.RESULT_UNFINISHED);
        assertTrue("a RESULT_UNFINISHED row must not count as played",
                !tied.hasBeenPlayed());
    }

    // ------------------------------------------------------------------
    // aggregate resolution
    // ------------------------------------------------------------------

    /**
     * A tie row on its own leaves the aggregate unresolved: it is not complete
     * and it nominates BOTH players as winners. That is precisely the state the
     * replay row exists to break -- without one, the section reports itself
     * complete (the tie row has been played) while two players "advance".
     */
    public void testTieRowAloneLeavesAggregateUnresolvedWithTwoWinners() {
        Tourney t = renjuTourney(930);
        TourneyRound round = t.createFirstRound(twoPlayers());
        SingleEliminationSection section =
                (SingleEliminationSection) round.getSection(1);

        // single-game set -> createRound emits ONE row, not a colour-swapped pair
        assertEquals("TB_RENJU is a single-game set: one row per pairing",
                1, section.getMatches().size());

        section.getMatches().get(0).setResult(TourneyMatch.RESULT_TIE);
        section.init();

        SingleEliminationMatch agg = onlyAggregate(section);
        assertEquals(TourneyMatch.RESULT_TIE, agg.getResult());
        assertTrue("a tied aggregate is not complete", !agg.isComplete());
        assertEquals("an unresolved tie nominates both players", 2,
                section.getWinners().size());
    }

    /**
     * Tie row + its (seat-swapped, seq+1) replay row fold into ONE aggregate:
     * open while the replay is unplayed -- so the round genuinely stays open
     * instead of closing on two winners -- and resolved to exactly one winner
     * once the replay is decisive.
     */
    public void testAggregateOpenWhileReplayUnplayedThenResolvesOnReplayWin() {
        Tourney t = renjuTourney(931);
        TourneyRound round = t.createFirstRound(twoPlayers());
        SingleEliminationSection section =
                (SingleEliminationSection) round.getSection(1);

        TourneyMatch original = section.getMatches().get(0);
        original.setResult(TourneyMatch.RESULT_TIE);

        // the replay row production's tie branch creates for a single-game set
        TourneyMatch replay = new SingleEliminationFormat()
                .createMoreMatchesAfterTie(original, true)[0];
        section.addMatch(replay);
        section.init();

        assertEquals("both rows belong to the section", 2, section.getMatches().size());
        assertEquals("rows for one pairing fold into one aggregate",
                1, section.getSingleEliminationMatches().size());

        // -- open state --
        assertTrue("section must stay open while the replay is unplayed",
                !section.isComplete());
        SingleEliminationMatch openAgg = onlyAggregate(section);
        assertTrue("aggregate must stay open while the replay is unplayed",
                !openAgg.isComplete());
        assertTrue("nobody advances while the replay is unplayed",
                section.getWinners().isEmpty());

        // -- replay decided: the swapped row's player1 (Bob) wins --
        assertEquals("replay row seats are swapped, so its player1 is Bob",
                BOB, replay.getPlayer1().getPlayerID());
        replay.setResult(TourneyMatch.RESULT_P1_WINS);
        section.init();

        assertTrue("section completes once the replay is played",
                section.isComplete());
        SingleEliminationMatch resolved = onlyAggregate(section);
        assertTrue("aggregate completes once the replay is decisive",
                resolved.isComplete());

        List<TourneyPlayerData> winners = section.getWinners();
        assertEquals("exactly one player advances", 1, winners.size());
        assertEquals("the replay winner advances", BOB,
                winners.get(0).getPlayerID());
    }

    /**
     * Mirror image: the replay decided the other way must advance Alice. Guards
     * against an assertion that passes only because of a fixed seat ordering.
     */
    public void testReplayLossAdvancesTheOtherPlayer() {
        Tourney t = renjuTourney(932);
        TourneyRound round = t.createFirstRound(twoPlayers());
        SingleEliminationSection section =
                (SingleEliminationSection) round.getSection(1);

        TourneyMatch original = section.getMatches().get(0);
        original.setResult(TourneyMatch.RESULT_TIE);

        TourneyMatch replay = new SingleEliminationFormat()
                .createMoreMatchesAfterTie(original, true)[0];
        replay.setResult(TourneyMatch.RESULT_P2_WINS);   // replay's player2 == Alice
        section.addMatch(replay);
        section.init();

        assertTrue(section.isComplete());
        List<TourneyPlayerData> winners = section.getWinners();
        assertEquals(1, winners.size());
        assertEquals(ALICE, winners.get(0).getPlayerID());
    }

    // ------------------------------------------------------------------
    // integration: the real call site (CacheTourneyStorer.applyMatchTo)
    // ------------------------------------------------------------------

    /**
     * Drives the fixed call site end-to-end through the storer: a TB_RENJU
     * single-elimination round-1 match is reported as RESULT_TIE via
     * CacheTourneyStorer.updateMatch, and the tie branch of applyMatchTo must
     * insert ONE replay row with seq+1, RESULT_UNFINISHED and the seats
     * SWAPPED, plus create the TB set that replay will be played in.
     *
     * The seats-swapped assertion is the load-bearing one: the 1-arg
     * createMoreMatchesAfterTie returns more[0] UNSWAPPED (the swap lives in
     * more[1], which the single-game-set path drops), so a regression back to
     * the 1-arg call would insert a row with player1 == Alice and fail here.
     */
    public void testUpdateMatchOnTieInsertsSwappedReplayAndCreatesItsSet()
            throws Throwable {

        final int eid = 940;
        Tourney t = renjuTourney(eid);
        TourneyRound round = t.createFirstRound(twoPlayers());
        assertEquals("single-game set -> one round-1 row",
                1, round.getSection(1).getMatches().size());

        // production takes match ids from the DB autoincrement; emulate that so
        // CacheTourneyStorer.findMatch can re-find the detached match by id.
        round.getSection(1).getMatches().get(0).setMatchID(1L);

        tourneyCache.insertTourney(t);   // persists the whole graph to Redis

        TourneyMatch detached = tourneyCache.getTourney(eid)
                .getRound(1).getSection(1).getMatches().get(0);
        final int originalSeq = detached.getSeq();
        assertEquals(ALICE, detached.getPlayer1().getPlayerID());
        assertEquals(BOB, detached.getPlayer2().getPlayerID());

        detached.setResult(TourneyMatch.RESULT_TIE);
        tourneyCache.updateMatch(detached);

        List<TourneyMatch> after = tourneyCache.getTourney(eid)
                .getRound(1).getSection(1).getMatches();
        assertEquals("tie must add exactly one replay row", 2, after.size());

        TourneyMatch kept = null;
        TourneyMatch replay = null;
        for (TourneyMatch m : after) {
            if (m.getSeq() == originalSeq) {
                kept = m;
            } else if (m.getSeq() == originalSeq + 1) {
                replay = m;
            }
        }
        assertNotNull("original row must survive with its seq", kept);
        assertEquals("original row keeps RESULT_TIE",
                TourneyMatch.RESULT_TIE, kept.getResult());

        assertNotNull("replay row must exist at seq = original + 1", replay);
        assertEquals("replay row must be unplayed",
                TourneyMatch.RESULT_UNFINISHED, replay.getResult());
        assertEquals("replay row event", eid, replay.getEvent());
        assertEquals("replay row round", 1, replay.getRound());
        // The load-bearing assertion: the 1-arg createMoreMatchesAfterTie would
        // leave more[0] unswapped (player1 == Alice) and fail these two.
        assertEquals("replay seats must be SWAPPED: player1 is Bob",
                BOB, replay.getPlayer1().getPlayerID());
        assertEquals("replay seats must be SWAPPED: player2 is Alice",
                ALICE, replay.getPlayer2().getPlayerID());

        // insertMatch(more[0]) must have created the TB set for the replay
        assertEquals("exactly one tournament set created (for the replay)",
                1, createdSets.size());
        TBSet replaySet = createdSets.get(0);
        assertEquals("replay set player1 is Bob", BOB, replaySet.getPlayer1Pid());
        assertEquals("replay set player2 is Alice", ALICE, replaySet.getPlayer2Pid());
        assertNotNull("replay set must have a first game", replaySet.getGames()[0]);
        assertEquals("replay game is TB_RENJU",
                GridStateFactory.TB_RENJU, replaySet.getGames()[0].getGame());
        assertEquals("replay game seats follow the swapped pairing",
                BOB, replaySet.getGames()[0].getPlayer1Pid());
        assertNull("single-game set: no second game",
                replaySet.getGames()[1]);

        // and the round must stay OPEN: the replay is unplayed
        assertTrue("round must stay open until the replay is played",
                !tourneyCache.getTourney(eid).getRound(1).isComplete());
    }
}
