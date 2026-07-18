package org.pente.webdb;

import java.io.IOException;
import java.util.*;

import jakarta.servlet.http.*;

import org.apache.log4j.*;

import org.pente.game.GameEventData;
import org.pente.game.GameSiteData;
import org.pente.game.GameTreeData;
import org.pente.game.GameVenueStorer;

import org.pente.webdb.dto.VenuesResponse;

/**
 * Endpoint logic for {@code GET /api/db/venues}.
 *
 * <p>Walks the production venue tree ({@code GameVenueStorer.getGameTree()},
 * a {@code Vector<GameTreeData>} of variant → site → event nodes) into the flat
 * {@link VenuesResponse} shape the search form consumes.
 */
public class VenuesHandler {

    private static Category cat =
            Category.getInstance(VenuesHandler.class.getName());

    private final GameVenueStorer gameVenueStorer;

    public VenuesHandler(GameVenueStorer gameVenueStorer) {
        this.gameVenueStorer = gameVenueStorer;
    }

    /** Servlet entry point. */
    public void handle(HttpServletRequest request,
                       HttpServletResponse response) throws IOException {
        try {
            JsonHttp.ok(response, venues());
        } catch (Exception e) {
            cat.error("venues failed", e);
            JsonHttp.error(response, 500, "server_error", "venue tree failed");
        }
    }

    /**
     * Build the venue tree response. Split out from {@link #handle} so the
     * DB-backed test can exercise it directly.
     */
    public VenuesResponse venues() throws Exception {

        VenuesResponse resp = new VenuesResponse();
        resp.variants = new ArrayList<VenuesResponse.Variant>();

        for (GameTreeData g : gameVenueStorer.getGameTree()) {
            VenuesResponse.Variant variant = new VenuesResponse.Variant();
            variant.game = g.getID();
            variant.name = g.getName();
            variant.sites = new ArrayList<VenuesResponse.Site>();

            List<GameSiteData> sites = g.getGameSiteData();
            if (sites != null) {
                for (GameSiteData s : sites) {
                    VenuesResponse.Site site = new VenuesResponse.Site();
                    site.id = s.getSiteID();
                    site.name = s.getName();
                    site.events = new ArrayList<VenuesResponse.Event>();

                    List<GameEventData> events = s.getGameEventData();
                    if (events != null) {
                        for (GameEventData e : events) {
                            VenuesResponse.Event ev = new VenuesResponse.Event();
                            ev.id = e.getEventID();
                            ev.name = e.getName();
                            site.events.add(ev);
                        }
                    }
                    variant.sites.add(site);
                }
            }
            resp.variants.add(variant);
        }
        return resp;
    }
}
