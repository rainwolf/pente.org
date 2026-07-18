package org.pente.webdb.dto;

import java.util.List;

/**
 * Wire request for {@code POST /api/db/collection/import} (auth).
 *
 * <pre>
 * {"games":[{"game":1,"player1":"x","player2":"y","winner":1,
 *            "site":null,"event":null,"round":null,"section":null,
 *            "playDate":null,"moves":[180,199,...]}]}
 * </pre>
 *
 * Each element is a {@link WebDbGameData} carrier: the games are parsed
 * client-side, so every item already carries its variant id and a full move
 * list (cell ints {@code 0..360}, {@code moves[0]} the center stone for non-Go
 * variants). Only the header fields plus {@code moves} are read on import;
 * {@code wgid}/{@code pid}/{@code imported} are DB-assigned and ignored here.
 *
 * <p>Public field, no getters/setters (Gson-friendly plain data carrier).
 */
public class ImportRequest {

    /** The games to import, in caller order (the index reported on errors). */
    public List<WebDbGameData> games;
}
