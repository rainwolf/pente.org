package org.pente.webdb.dto;

import java.util.Date;

/**
 * Header metadata for a saved analysis tree — everything in {@code webdb_analysis}
 * except the (potentially large) {@code tree} JSON blob. Returned by
 * {@code MySQLWebDbStorer.listAnalyses}; also filled in as the out-parameter of
 * {@code loadAnalysis}, which returns the {@code tree} JSON separately.
 *
 * <p>Public fields, no getters/setters (Gson-friendly plain data carrier).
 */
public class AnalysisMeta {

    /** Auto-increment primary key; {@code 0} until stored. */
    public long aid;

    /** User-supplied analysis name. */
    public String name;

    /** Game/variant id the analysis is for. */
    public int game;

    /** DB-assigned creation timestamp. */
    public Date created;

    /** DB-assigned last-update timestamp (auto-bumped on update). */
    public Date updated;
}
