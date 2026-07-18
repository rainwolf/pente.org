package org.pente.webdb.dto;

import java.util.Date;

/**
 * A game in a player's personal collection — the {@code webdb_game} row plus its
 * reconstructed move list. Mirrors the {@code webdb_game} columns one-for-one and
 * carries {@code int[] moves} (cell ids {@code 0..360} in the caller's board
 * orientation, first move the center {@code 180} for non-Go variants).
 *
 * <p>Public fields, no getters/setters: this is a plain data carrier passed
 * between {@code MySQLWebDbStorer} and the Task 6 HTTP handlers (Gson-friendly).
 * {@code wgid} and {@code imported} are DB-assigned on {@code storeGame}; the
 * "list" query populates only the header fields and leaves {@code moves} null,
 * while {@code loadGame} fills {@code moves} in.
 */
public class WebDbGameData {

    /** Auto-increment primary key; {@code 0} until stored. */
    public long wgid;

    /** Owning player id (FK to the site {@code player}/{@code dsg_player}). */
    public long pid;

    /** Game/variant id (see {@code GridStateFactory}, e.g. {@code PENTE == 1}). */
    public int game;

    /** Display name of seat 1. */
    public String player1;

    /** Display name of seat 2. */
    public String player2;

    /** {@code 0} unknown, {@code 1} player1, {@code 2} player2. */
    public int winner;

    /** Optional venue metadata (nullable). */
    public String site;
    public String event;
    public String round;
    public String section;

    /** When the game was played (nullable). */
    public Date playDate;

    /** When the row was inserted (DB-assigned). */
    public Date imported;

    /** Move list in the caller's orientation; {@code moves[0]} is the center. */
    public int[] moves;
}
