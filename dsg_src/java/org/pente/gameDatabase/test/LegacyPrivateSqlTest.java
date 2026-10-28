package org.pente.gameDatabase.test;

import junit.framework.TestCase;
import org.pente.gameDatabase.MySQLGameStorerSearcher;

/**
 * Pins the SQL built by the legacy mobile/desktop searcher. No DB involved.
 */
public class LegacyPrivateSqlTest extends TestCase {

    private static final String T = "pente_move";
    private static final String FROM_G = ", pente_game g , player p1 ";
    private static final String WHERE_G = "and m.gid = g.gid and g.player1_pid = p1.pid and (p1.name_lower = ?  ) and g.private = 'N' ";
    private static final String FROM_G2 = ", pente_game g , player p1 ";
    private static final String WHERE_G2 = "and m.gid = g.gid and g.player2_pid = p1.pid and (p1.name_lower = ?  ) ";

    public LegacyPrivateSqlTest(String name) {
        super(name);
    }

    // ---- full SQL pins (commit af36e47 pinned the pre-private output; these add only m.private = 'N') ----

    public void testStatsUnfilteredUnchanged() {
        assertEquals(
                "select m.next_move, m.rotation, m.winner, count(*) from pente_move m " +
                        "where m.hash_key = 123 and m.move_num = 4 and m.game = 51 and m.private = 'N' " +
                        "group by m.next_move, m.rotation, m.winner",
                MySQLGameStorerSearcher.statsSql(T, 123L, 4, 51, false, "", "", "", "", false));
    }

    public void testStatsFilteredUnchanged() {
        assertEquals(
                "select m.next_move, m.rotation, m.winner, count(*) from pente_move m , pente_game g , player p1 " +
                        "where m.hash_key = 123 and m.move_num = 4 and m.game = 51 and m.private = 'N' and g.game = 51 " +
                        "and m.gid = g.gid and g.player1_pid = p1.pid and (p1.name_lower = ?  ) and g.private = 'N' " +
                        "group by m.next_move, m.rotation, m.winner",
                MySQLGameStorerSearcher.statsSql(T, 123L, 4, 51, true, FROM_G, WHERE_G, "", "", false));
    }

    public void testStatsUnionUnchanged() {
        assertEquals(
                "select nextm, rot, win, count(*) from ( " +
                        "select * from (select m.next_move as nextm, m.rotation as rot, m.winner as win " +
                        "from pente_move m , pente_game g , player p1 " +
                        "where m.hash_key = 123 and m.move_num = 4 and m.game = 51 and m.private = 'N' and g.game = 51 " +
                        "and m.gid = g.gid and g.player1_pid = p1.pid and (p1.name_lower = ?  ) and g.private = 'N' ) as d1 " +
                        "union all " +
                        "select * from (select m.next_move as nextm, m.rotation as rot, m.winner as win " +
                        "from pente_move m , pente_game g , player p1 " +
                        "where m.hash_key = 123 and m.move_num = 4 and m.game = 51 and m.private = 'N' and g.game = 51 " +
                        "and m.gid = g.gid and g.player2_pid = p1.pid and (p1.name_lower = ?  ) ) as d2 " +
                        ") as main group by nextm, rot, win",
                MySQLGameStorerSearcher.statsSql(T, 123L, 4, 51, true, FROM_G, WHERE_G, FROM_G2, WHERE_G2, true));
    }

    public void testMatchingUnfilteredUnchanged() {
        assertEquals(
                "select m.gid from pente_move m , pente_game g where m.hash_key = 123 and m.move_num = 4 " +
                        "and m.game = 51 and m.private = 'N' and g.game = 51 and m.gid = g.gid and g.private = 'N' " +
                        "order by m.play_date desc limit 0, 10",
                MySQLGameStorerSearcher.matchingGamesSql(T, 123L, 4, 51, true,
                        ", pente_game g ", "and m.gid = g.gid and g.private = 'N' ", "", "", false, "0", "10"));
    }

    public void testMatchingFilteredUnchanged() {
        assertEquals(
                "select m.gid from pente_move m , pente_game g , player p1 where m.hash_key = 123 and m.move_num = 4 " +
                        "and m.game = 51 and m.private = 'N' and g.game = 51 " +
                        "and m.gid = g.gid and g.player1_pid = p1.pid and (p1.name_lower = ?  ) and g.private = 'N' " +
                        "order by m.play_date desc limit 0, 10",
                MySQLGameStorerSearcher.matchingGamesSql(T, 123L, 4, 51, true, FROM_G, WHERE_G, "", "", false, "0", "10"));
    }

    public void testMatchingUnionUnchanged() {
        assertEquals(
                "select gid from ( select m.gid as gid, m.play_date as play_d from pente_move m , pente_game g , player p1 " +
                        "where m.hash_key = 123 and m.move_num = 4 and m.game = 51 and m.private = 'N' and g.game = 51 " +
                        "and m.gid = g.gid and g.player1_pid = p1.pid and (p1.name_lower = ?  ) and g.private = 'N' " +
                        "union all select m.gid, m.play_date as play_d from pente_move m , pente_game g , player p1 " +
                        "where m.hash_key = 123 and m.move_num = 4 and m.game = 51 and m.private = 'N' and g.game = 51 " +
                        "and m.gid = g.gid and g.player2_pid = p1.pid and (p1.name_lower = ?  ) " +
                        ") as main order by play_d desc limit 0, 10",
                MySQLGameStorerSearcher.matchingGamesSql(T, 123L, 4, 51, true, FROM_G, WHERE_G, FROM_G2, WHERE_G2, true, "0", "10"));
    }

    // ---- m.private = 'N' on every arm ----

    private static int count(String hay, String needle) {
        int n = 0;
        for (int i = hay.indexOf(needle); i >= 0; i = hay.indexOf(needle, i + 1)) n++;
        return n;
    }

    public void testStatsFiltersPrivate() {
        String sql = MySQLGameStorerSearcher.statsSql(T, 123L, 4, 51, false, "", "", "", "", false);
        assertTrue(sql, sql.contains("and m.game = 51 and m.private = 'N' "));
        assertTrue(sql, sql.indexOf("pente_game") < 0);
    }

    public void testMatchingGamesFiltersPrivate() {
        String sql = MySQLGameStorerSearcher.matchingGamesSql(T, 123L, 4, 51, true,
                FROM_G, WHERE_G, "", "", false, "0", "10");
        assertTrue(sql, sql.contains("and m.game = 51 and m.private = 'N' "));
        assertTrue(sql, sql.contains("g.private = 'N'"));
    }

    public void testUnionArmsFilterPrivate() {
        String stats = MySQLGameStorerSearcher.statsSql(T, 123L, 4, 51, true, FROM_G, WHERE_G, FROM_G2, WHERE_G2, true);
        String page = MySQLGameStorerSearcher.matchingGamesSql(T, 123L, 4, 51, true, FROM_G, WHERE_G, FROM_G2, WHERE_G2, true, "0", "10");
        assertEquals(stats, 2, count(stats, "and m.game = 51 and m.private = 'N' "));
        assertEquals(page, 2, count(page, "and m.game = 51 and m.private = 'N' "));
    }
}
