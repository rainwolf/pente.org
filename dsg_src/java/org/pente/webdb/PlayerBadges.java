package org.pente.webdb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.apache.log4j.Category;

import org.pente.gameServer.core.MySQLDSGPlayerStorer;
import org.pente.gameServer.core.SimpleDSGPlayerData;
import org.pente.gameServer.core.SimpleDSGPlayerGameData;
import org.pente.gameServer.mobile.MobileJsonHelper;
import org.pente.webdb.dto.GameHeader;

/**
 * Fills {@link GameHeader.Player#nameColor} and {@link GameHeader.Player#crown}
 * for a page of headers with three batched {@code IN (...)} queries on the
 * caller's (replica) connection. The colour and crown rules are not copied:
 * each player is assembled into a {@link SimpleDSGPlayerData} and read through
 * {@link MobileJsonHelper#playerColorNonZero} and
 * {@link SimpleDSGPlayerData#getTourneyWinner()}, as the mobile API does.
 *
 * <p>Badges are decoration: any failure is logged and leaves every badge 0, and
 * results are assigned only after all three queries succeed (never a partial set).
 */
public final class PlayerBadges {

    private static final Category cat = Category.getInstance(PlayerBadges.class.getName());

    private PlayerBadges() {
    }

    /** Apply badges to {@code headers} using {@code con}; never throws. */
    public static void apply(Connection con, List<GameHeader> headers) {
        apply(con, headers, System.currentTimeMillis());
    }

    /**
     * As {@link #apply(Connection, List)} with a fixed clock for the
     * subscription windows. Public only so the tests in
     * {@code org.pente.webdb.test} can pin {@code now}.
     */
    public static void apply(Connection con, List<GameHeader> headers, long now) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        TreeSet<Long> pids = new TreeSet<Long>();
        for (GameHeader h : headers) {
            collect(pids, h.player1);
            collect(pids, h.player2);
        }
        if (pids.isEmpty()) {
            return;
        }
        Map<Long, int[]> badges;
        try {
            badges = compute(con, new ArrayList<Long>(pids), now);
        } catch (Exception e) {
            cat.warn("header badges skipped for " + pids.size() + " players", e);
            return;
        }
        for (GameHeader h : headers) {
            assign(badges, h.player1);
            assign(badges, h.player2);
        }
    }

    private static void collect(TreeSet<Long> pids, GameHeader.Player p) {
        if (p != null && p.pid > 0) {
            pids.add(Long.valueOf(p.pid));
        }
    }

    private static void assign(Map<Long, int[]> badges, GameHeader.Player p) {
        if (p == null || p.pid <= 0) {
            return;
        }
        int[] b = badges.get(Long.valueOf(p.pid));
        if (b != null) {
            p.nameColor = b[0];
            p.crown = b[1];
        }
    }

    /** pid -> {nameColor, crown}, only for pids with a {@code dsg_player} row. */
    private static Map<Long, int[]> compute(Connection con, List<Long> pids, long now)
            throws Exception {

        String in = placeholders(pids.size());
        Map<Long, SimpleDSGPlayerData> players = new HashMap<Long, SimpleDSGPlayerData>();
        Map<Long, Integer> levels = new HashMap<Long, Integer>();

        Calendar lastMonth = Calendar.getInstance();
        lastMonth.setTimeInMillis(now);
        lastMonth.add(Calendar.DATE, -31);
        Calendar lastYear = Calendar.getInstance();
        lastYear.setTimeInMillis(now);
        lastYear.add(Calendar.YEAR, -1);

        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            stmt = prepare(con, "select pid, name_color from dsg_player where pid in " + in, pids);
            rs = stmt.executeQuery();
            while (rs.next()) {
                SimpleDSGPlayerData d = new SimpleDSGPlayerData();
                d.setNameColorRGB(rs.getInt(2));
                players.put(Long.valueOf(rs.getLong(1)), d);
            }
            close(rs, stmt);
            rs = null;
            stmt = null;

            stmt = prepare(con, "select pid, tourney_winner from dsg_player_game where pid in "
                    + in + " and tourney_winner > 0", pids);
            rs = stmt.executeQuery();
            while (rs.next()) {
                SimpleDSGPlayerData d = players.get(Long.valueOf(rs.getLong(1)));
                if (d != null) {
                    SimpleDSGPlayerGameData g = new SimpleDSGPlayerGameData();
                    g.setTourneyWinner(rs.getInt(2));
                    d.addPlayerGameData(g);
                }
            }
            close(rs, stmt);
            rs = null;
            stmt = null;

            stmt = prepare(con, "select pid, level, paymentdate from dsg_subscribers where pid in "
                    + in, pids);
            rs = stmt.executeQuery();
            while (rs.next()) {
                Long pid = Long.valueOf(rs.getLong(1));
                int bits = MySQLDSGPlayerStorer.activeSubscriberBits(
                        rs.getInt(2), rs.getDate(3), lastMonth, lastYear);
                Integer prev = levels.get(pid);
                levels.put(pid, Integer.valueOf((prev == null ? 0 : prev.intValue()) | bits));
            }
        } finally {
            close(rs, stmt);
        }

        Map<Long, int[]> out = new HashMap<Long, int[]>();
        for (Map.Entry<Long, SimpleDSGPlayerData> e : players.entrySet()) {
            SimpleDSGPlayerData d = e.getValue();
            Integer lvl = levels.get(e.getKey());
            d.setSubscriberLevel(lvl == null ? 0 : lvl.intValue());
            out.put(e.getKey(), new int[]{
                    MobileJsonHelper.playerColorNonZero(d), d.getTourneyWinner()});
        }
        return out;
    }

    private static PreparedStatement prepare(Connection con, String sql, List<Long> pids)
            throws Exception {
        PreparedStatement stmt = con.prepareStatement(sql);
        for (int i = 0; i < pids.size(); i++) {
            stmt.setLong(i + 1, pids.get(i).longValue());
        }
        return stmt;
    }

    private static String placeholders(int n) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < n; i++) {
            sb.append(i == 0 ? "?" : ",?");
        }
        return sb.append(")").toString();
    }

    private static void close(ResultSet rs, PreparedStatement stmt) {
        if (rs != null) {
            try {
                rs.close();
            } catch (Exception ignore) {
            }
        }
        if (stmt != null) {
            try {
                stmt.close();
            } catch (Exception ignore) {
            }
        }
    }
}
