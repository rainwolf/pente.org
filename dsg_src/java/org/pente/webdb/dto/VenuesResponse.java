package org.pente.webdb.dto;

import java.util.List;

/**
 * Wire response for {@code GET /api/db/venues} — the venue tree used to
 * populate the search form's site/event pickers.
 *
 * <pre>
 * {"variants": [
 *    {"game": 1, "name": "Pente",
 *     "sites": [
 *        {"id": 2, "name": "Pente.org",
 *         "events": [{"id": 7, "name": "..."}]}]}]}
 * </pre>
 */
public class VenuesResponse {

    /** One entry per game variant that has any stored venue data. */
    public List<Variant> variants;

    public static class Variant {
        /** Game/variant id. */
        public int game;
        /** Variant display name (e.g. {@code "Pente"}). */
        public String name;
        public List<Site> sites;
    }

    public static class Site {
        public int id;
        public String name;
        public List<Event> events;
    }

    public static class Event {
        public int id;
        public String name;
    }
}
