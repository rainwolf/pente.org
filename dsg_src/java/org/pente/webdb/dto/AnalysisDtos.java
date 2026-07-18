package org.pente.webdb.dto;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import com.google.gson.JsonElement;

/**
 * Wire request/response carriers for the analyses CRUD endpoints
 * ({@code /api/db/analyses}). Grouped here (one public top-level class with
 * static nested carriers) so the whole small contract lives in one file.
 *
 * <p>The analysis {@code tree} travels as a nested JSON value ({@link JsonElement}),
 * NOT a quoted string: Gson deserializes the request's {@code tree} object into
 * a {@code JsonElement} and serializes the response's back inline. The server
 * persists the tree as its raw JSON text (see {@code MySQLWebDbStorer}); the
 * handler bridges the two with {@code tree.toString()} on the way in and a
 * re-parse on the way out.
 *
 * <p>Timestamps are ISO 8601 UTC ({@code yyyy-MM-dd'T'HH:mm:ss'Z'}), matching
 * {@link GameHeader}'s {@code playDate}, rather than Gson's default locale
 * {@code Date} rendering.
 */
public final class AnalysisDtos {

    private AnalysisDtos() {
    }

    /** {@code POST /api/db/analyses} body: {@code {"name":..,"game":..,"tree":{..}}}. */
    public static class CreateRequest {
        public String name;
        public int game;
        public JsonElement tree;
    }

    /** {@code POST /api/db/analyses} response: {@code {"aid":7}}. */
    public static class CreateResponse {
        public long aid;

        public CreateResponse() {
        }

        public CreateResponse(long aid) {
            this.aid = aid;
        }
    }

    /**
     * {@code PUT /api/db/analyses/{aid}} body: {@code {"name"?, "tree"?}}. Either
     * field may be absent (null); absent fields are left unchanged.
     */
    public static class UpdateRequest {
        public String name;
        public JsonElement tree;
    }

    /** One row in the analyses listing (metadata only, no tree). */
    public static class ListItem {
        public long aid;
        public String name;
        public int game;
        public String created;
        public String updated;

        public static ListItem from(AnalysisMeta m) {
            ListItem i = new ListItem();
            i.aid = m.aid;
            i.name = m.name;
            i.game = m.game;
            i.created = isoUtc(m.created);
            i.updated = isoUtc(m.updated);
            return i;
        }
    }

    /** {@code GET /api/db/analyses} response: {@code {"analyses":[ListItem,..]}}. */
    public static class ListResponse {
        public List<ListItem> analyses;
    }

    /**
     * {@code GET /api/db/analyses/{aid}} response:
     * {@code {"aid":7,"name":..,"game":..,"tree":{..}}}.
     */
    public static class DetailResponse {
        public long aid;
        public String name;
        public int game;
        public JsonElement tree;
    }

    private static String isoUtc(Date d) {
        if (d == null) {
            return null;
        }
        // SimpleDateFormat is not thread-safe; build one per call.
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt.format(d);
    }
}
