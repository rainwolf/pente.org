package org.pente.webdb;

import java.io.IOException;
import java.sql.*;
import java.util.*;

import jakarta.servlet.http.*;

import org.apache.log4j.*;

import org.pente.database.DBHandler;

/**
 * Endpoint logic for {@code GET /api/db/players?q=al} — player-name
 * autocomplete.
 *
 * <p>Prefix-matches the {@code player.name_lower} column (which is indexed) and
 * returns up to 20 display names ordered by {@code name_lower}. Queries shorter
 * than two characters are rejected (avoids a near-full-table prefix scan).
 */
public class PlayersHandler {

    private static Category cat =
            Category.getInstance(PlayersHandler.class.getName());

    /** Minimum query length; shorter queries are a 400. */
    private static final int MIN_QUERY = 2;
    /** Max names returned. */
    private static final int LIMIT = 20;

    private final DBHandler dbHandler;

    public PlayersHandler(DBHandler dbHandler) {
        this.dbHandler = dbHandler;
    }

    /** Servlet entry point. Reads {@code ?q=}. */
    public void handle(HttpServletRequest request,
                       HttpServletResponse response) throws IOException {

        String q = request.getParameter("q");
        try {
            List<String> names = players(q);
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("players", names);
            JsonHttp.ok(response, body);
        } catch (IllegalArgumentException e) {
            JsonHttp.error(response, HttpServletResponse.SC_BAD_REQUEST,
                    "bad_request", e.getMessage());
        } catch (Exception e) {
            cat.error("players autocomplete failed", e);
            JsonHttp.error(response, 500, "server_error", "player search failed");
        }
    }

    /**
     * Prefix-match player names. Throws {@link IllegalArgumentException} when
     * {@code q} is shorter than {@value #MIN_QUERY} characters. Split out from
     * {@link #handle} so the DB-backed test can exercise it directly.
     */
    public List<String> players(String q) throws Exception {

        String trimmed = (q == null) ? "" : q.trim();
        if (trimmed.length() < MIN_QUERY) {
            throw new IllegalArgumentException(
                    "q must be at least " + MIN_QUERY + " characters");
        }

        List<String> names = new ArrayList<String>();
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            con = dbHandler.getConnection();
            // name_lower is indexed; LIMIT is a fixed constant, not user input.
            // Escape LIKE metacharacters in the user input so a query like "%%"
            // is matched as a literal prefix (returning nothing) rather than a
            // full-table wildcard scan that defeats the 2-char minimum. F5.
            stmt = con.prepareStatement("select name from player "
                    + "where name_lower like ? escape '\\\\' "
                    + "order by name_lower limit " + LIMIT);
            stmt.setString(1, escapeLike(trimmed.toLowerCase()) + "%");
            rs = stmt.executeQuery();
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (SQLException e) {
                    // ignore
                }
            }
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException e) {
                    // ignore
                }
            }
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
        return names;
    }

    /**
     * Escape the LIKE metacharacters {@code %}, {@code _} and the escape char
     * {@code \} itself so user input is matched literally under {@code ESCAPE
     * '\'}. Only the trailing prefix wildcard is left unescaped by the caller.
     */
    private static String escapeLike(String s) {
        StringBuilder b = new StringBuilder(s.length() + 4);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == '%' || c == '_') {
                b.append('\\');
            }
            b.append(c);
        }
        return b.toString();
    }
}
