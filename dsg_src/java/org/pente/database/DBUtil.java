package org.pente.database;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Small JDBC helpers shared across storers. */
public final class DBUtil {
    private DBUtil() {}

    /**
     * Reads a numeric-valued ENUM/CHAR column as an int, tolerating the
     * ENUM invalid-value sentinel. Returns 0 for SQL NULL or a blank value
     * (matching the lenient getInt('') behavior of the legacy MySQL driver);
     * otherwise parses the trimmed value.
     */
    public static int enumInt(ResultSet rs, int col) throws SQLException {
        String v = rs.getString(col);
        if (v == null) return 0;
        v = v.trim();
        return v.isEmpty() ? 0 : Integer.parseInt(v);
    }
}
