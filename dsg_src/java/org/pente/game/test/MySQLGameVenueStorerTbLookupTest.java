package org.pente.game.test;

import java.util.*;

import junit.framework.*;

import org.pente.game.*;

/**
 * Regression test for the turn-based -> base game id translation in
 * MySQLGameVenueStorer's venue-tree lookup (findGameTreeData).
 *
 * The venue tree only holds base (live) game ids.  Turn-based ids are base + 50
 * (GridStateFactory.TB_START), and the lookup historically translated them
 * (tree.get(game - 1 - 50)).  When the lookup was rewritten to resolve nodes by
 * id, that translation was dropped, so every turn-based lookup returned null.
 * That made creating a tournament -- whose game id is turn-based -- throw a
 * NullPointerException in MySQLGameVenueStorer.addGameEventData ("siteData is
 * null"), via MySQLTourneyStorer.insertTourney.
 *
 * No database: the storer is built around a hand-made venue tree (one base Pente
 * node), mirroring what updateGameTree() synthesizes from a "Live Game" event.
 */
public class MySQLGameVenueStorerTbLookupTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{
                MySQLGameVenueStorerTbLookupTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(MySQLGameVenueStorerTbLookupTest.class);
    }

    public MySQLGameVenueStorerTbLookupTest(String name) {
        super(name);
    }

    private static final String SITE = DSG2_12GameFormat.SITE_NAME; // "Pente.org"
    private static final int SITE_ID = 2;

    private MySQLGameVenueStorer storerWithPenteNode() {
        GameSiteData site = new SimpleGameSiteData();
        site.setSiteID(SITE_ID);
        site.setName(SITE);

        GameEventData live = new SimpleGameEventData();
        live.setEventID(7);
        live.setName(MySQLGameVenueStorer.LIVE_EVENT);
        live.setGame(GridStateFactory.PENTE);
        site.addGameEventData(live);

        GameTreeData node = new SimpleGameTreeData();
        node.setID(GridStateFactory.PENTE);
        node.addGameSiteData(site);

        Vector<GameTreeData> tree = new Vector<GameTreeData>();
        tree.add(node);
        return new MySQLGameVenueStorer(tree);
    }

    // Sanity: the base (live) id resolves, so the fixture is wired correctly.
    public void testBaseIdResolvesSiteData() throws Exception {
        MySQLGameVenueStorer storer = storerWithPenteNode();
        assertNotNull(storer.getGameSiteData(GridStateFactory.PENTE, SITE));
    }

    // The regression: a turn-based id (base + 50) must resolve to its base node.
    // Before the fix this returned null and tournament creation NPE'd.
    public void testTurnBasedIdResolvesToBaseNodeByName() throws Exception {
        MySQLGameVenueStorer storer = storerWithPenteNode();
        GameSiteData byTb =
                storer.getGameSiteData(GridStateFactory.TB_PENTE, SITE);
        assertNotNull("turn-based id should resolve to its base venue node", byTb);
        assertEquals(SITE_ID, byTb.getSiteID());
    }

    // getGameSiteData(int, int) shares the same lookup and must translate too.
    public void testTurnBasedIdResolvesBySiteId() throws Exception {
        MySQLGameVenueStorer storer = storerWithPenteNode();
        assertNotNull(storer.getGameSiteData(GridStateFactory.TB_PENTE, SITE_ID));
    }

    // getGameEventData routes through the same lookup; this is the exact path the
    // tournament insert (MySQLTourneyStorer -> addGameEventData) depends on.
    public void testTurnBasedEventLookupResolves() throws Exception {
        MySQLGameVenueStorer storer = storerWithPenteNode();
        GameEventData ev = storer.getGameEventData(
                GridStateFactory.TB_PENTE, MySQLGameVenueStorer.LIVE_EVENT, SITE);
        assertNotNull(ev);
        assertEquals(MySQLGameVenueStorer.LIVE_EVENT, ev.getName());
    }
}
