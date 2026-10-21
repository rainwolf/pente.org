package org.pente.gameServer.kids.test;

import java.util.Date;
import java.util.List;

import junit.framework.TestCase;

import org.pente.game.GridStateFactory;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.SimpleDSGPlayerData;
import org.pente.gameServer.kids.KidActiveGames;
import org.pente.turnBased.TBGame;
import org.pente.turnBased.TBSet;

public class KidActiveGamesTest extends TestCase {

    private static final long KID = 100;
    private static final long STRANGER = 200;
    private static final long OTHER = 300;

    private MapPlayerStorer players;

    public KidActiveGamesTest(String name) {
        super(name);
    }

    protected void setUp() {
        players = new MapPlayerStorer();
        players.put(player(KID, "kid"));
        players.put(player(STRANGER, "stranger"));
        players.put(player(OTHER, "other"));
    }

    private static SimpleDSGPlayerData player(long pid, String name) {
        SimpleDSGPlayerData d = new SimpleDSGPlayerData();
        d.setPlayerID(pid);
        d.setName(name);
        d.setStatus(DSGPlayerData.ACTIVE);
        return d;
    }

    private static TBGame game(long gid, long p1, long p2, char state, int moves, long millis) {
        TBGame g = new TBGame();
        g.setGame(GridStateFactory.TB_PENTE);
        g.setGid(gid);
        g.setPlayer1Pid(p1);
        g.setPlayer2Pid(p2);
        g.setState(state);
        for (int i = 0; i < moves; i++) {
            g.addMove(180 + i);
        }
        g.setLastMoveDate(new Date(millis));
        return g;
    }

    private static TBSet set(TBGame... games) {
        TBSet s = new TBSet(1);
        for (TBGame g : games) {
            s.addGame(g);
        }
        return s;
    }

    private List<KidActiveGames.Entry> build(TBSet... sets) throws Exception {
        return KidActiveGames.build(KID, java.util.Arrays.asList(sets), players);
    }

    public void testOnlyActiveGamesListed() throws Exception {
        TBSet s = set(game(1, STRANGER, KID, TBGame.STATE_ACTIVE, 1, 1000),
                game(2, KID, STRANGER, 'C', 5, 1000));
        TBSet notStarted = set(game(3, STRANGER, KID, 'N', 0, 1000));
        List<KidActiveGames.Entry> list = build(s, notStarted);
        assertEquals(1, list.size());
        assertEquals(1L, list.get(0).gid);
    }

    public void testEntryFields() throws Exception {
        TBSet s = set(game(7, STRANGER, KID, TBGame.STATE_ACTIVE, 1, 1234567L));
        s.setPrivateGame(true);
        KidActiveGames.Entry e = build(s).get(0);
        assertEquals(7L, e.gid);
        assertEquals(GridStateFactory.TB_PENTE, e.game);
        assertEquals(GridStateFactory.getGameName(GridStateFactory.TB_PENTE), e.gameName);
        assertEquals(STRANGER, e.opponentPid);
        assertEquals("stranger", e.opponentName);
        assertTrue(e.kidsTurn);
        assertEquals(1234567L, e.lastMoveDate);
        assertTrue(e.privateGame);
        TBSet open = set(game(8, STRANGER, KID, TBGame.STATE_ACTIVE, 1, 5));
        assertTrue(!build(open).get(0).privateGame);
    }

    public void testTwoGameSetListsBoth() throws Exception {
        TBSet s = set(game(1, STRANGER, KID, TBGame.STATE_ACTIVE, 1, 1000),
                game(2, KID, STRANGER, TBGame.STATE_ACTIVE, 1, 1000));
        assertEquals(2, build(s).size());
    }

    public void testSortedByLastMoveDesc() throws Exception {
        TBSet a = set(game(1, STRANGER, KID, TBGame.STATE_ACTIVE, 1, 1000));
        TBSet b = set(game(2, STRANGER, KID, TBGame.STATE_ACTIVE, 1, 2000));
        TBSet c = set(game(3, STRANGER, KID, TBGame.STATE_ACTIVE, 1, 3000));
        List<KidActiveGames.Entry> list = build(a, c, b);
        assertEquals(3L, list.get(0).gid);
        assertEquals(2L, list.get(1).gid);
        assertEquals(1L, list.get(2).gid);
    }

    public void testTiesByGidAscending() throws Exception {
        TBSet s = set(game(9, STRANGER, KID, TBGame.STATE_ACTIVE, 1, 1000),
                game(4, KID, STRANGER, TBGame.STATE_ACTIVE, 1, 1000));
        List<KidActiveGames.Entry> list = build(s);
        assertEquals(4L, list.get(0).gid);
        assertEquals(9L, list.get(1).gid);
    }

    public void testMissingOpponentSkipped() throws Exception {
        TBSet s = set(game(1, 999, KID, TBGame.STATE_ACTIVE, 1, 1000));
        assertEquals(0, build(s).size());
    }

    public void testOtherPlayersGamesIgnored() throws Exception {
        TBSet s = set(game(1, STRANGER, OTHER, TBGame.STATE_ACTIVE, 1, 1000));
        assertEquals(0, build(s).size());
    }
}
