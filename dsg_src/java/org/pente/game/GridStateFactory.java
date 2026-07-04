package org.pente.game;

import org.apache.commons.lang3.ArrayUtils;

public class GridStateFactory {

    public static final int PENTE = 1;
    public static final int SPEED_PENTE = PENTE + 1;
    public static final int KERYO = 3;
    public static final int SPEED_KERYO = KERYO + 1;
    public static final int GOMOKU = 5;
    public static final int SPEED_GOMOKU = GOMOKU + 1;
    public static final int DPENTE = 7;
    public static final int SPEED_DPENTE = DPENTE + 1;
    public static final int GPENTE = 9;
    public static final int SPEED_GPENTE = GPENTE + 1;
    public static final int POOF_PENTE = 11;
    public static final int SPEED_POOF_PENTE = POOF_PENTE + 1;
    public static final int CONNECT6 = 13;
    public static final int SPEED_CONNECT6 = CONNECT6 + 1;
    public static final int BOAT_PENTE = 15;
    public static final int SPEED_BOAT_PENTE = BOAT_PENTE + 1;
    public static final int DKERYO = 17;
    public static final int SPEED_DKERYO = DKERYO + 1;
    public static final int GO = 19;
    public static final int SPEED_GO = GO + 1;
    public static final int GO9 = 21;
    public static final int SPEED_GO9 = GO9 + 1;
    public static final int GO13 = 23;
    public static final int SPEED_GO13 = GO13 + 1;
    public static final int OPENTE = 25;
    public static final int SPEED_OPENTE = OPENTE + 1;
    public static final int SWAP2PENTE = 27;
    public static final int SPEED_SWAP2PENTE = SWAP2PENTE + 1;
    public static final int SWAP2KERYO = 29;
    public static final int SPEED_SWAP2KERYO = SWAP2KERYO + 1;
    public static final int RENJU = 31;
    public static final int SPEED_RENJU = RENJU + 1;

    public static final int[] LIVE_GAMES = {
            PENTE, SPEED_PENTE,
            KERYO, SPEED_KERYO,
            GOMOKU, SPEED_GOMOKU,
            DPENTE, SPEED_DPENTE,
            GPENTE, SPEED_GPENTE,
            POOF_PENTE, SPEED_POOF_PENTE,
            CONNECT6, SPEED_CONNECT6,
            BOAT_PENTE, SPEED_BOAT_PENTE,
            DKERYO, SPEED_DKERYO,
            GO, SPEED_GO,
            GO9, SPEED_GO9,
            GO13, SPEED_GO13,
            OPENTE, SPEED_OPENTE,
            SWAP2PENTE, SPEED_SWAP2PENTE,
            SWAP2KERYO, SPEED_SWAP2KERYO,
            RENJU, SPEED_RENJU
    };

    // 50 + normal game for turn-based games
    // only used for separate ratings
    private static final int TB_START = 50;
    public static final int TB_PENTE = TB_START + PENTE;
    public static final int TB_KERYO = TB_START + KERYO;
    public static final int TB_GOMOKU = TB_START + GOMOKU;
    public static final int TB_DPENTE = TB_START + DPENTE;
    public static final int TB_GPENTE = TB_START + GPENTE;
    public static final int TB_POOF_PENTE = TB_START + POOF_PENTE;
    public static final int TB_CONNECT6 = TB_START + CONNECT6;
    public static final int TB_BOAT_PENTE = TB_START + BOAT_PENTE;
    public static final int TB_DKERYO = TB_START + DKERYO;
    public static final int TB_GO = TB_START + GO;
    public static final int TB_GO9 = TB_START + GO9;
    public static final int TB_GO13 = TB_START + GO13;
    public static final int TB_OPENTE = TB_START + OPENTE;
    public static final int TB_SWAP2PENTE = TB_START + SWAP2PENTE;
    public static final int TB_SWAP2KERYO = TB_START + SWAP2KERYO;
    public static final int TB_RENJU = TB_START + RENJU;

    public static final int[] TB_GAMES = {
            TB_PENTE, TB_KERYO, TB_GOMOKU, TB_DPENTE, TB_GPENTE, TB_POOF_PENTE,
            TB_CONNECT6, TB_BOAT_PENTE, TB_DKERYO, TB_GO, TB_GO9, TB_GO13,
            TB_OPENTE, TB_SWAP2PENTE, TB_SWAP2KERYO, TB_RENJU
    };

    public final static int[] ALL_GAMES = ArrayUtils.addAll(TB_GAMES, LIVE_GAMES);


    public static final Game PENTE_GAME = new Game(PENTE, "Pente", false);
    public static final Game SPEED_PENTE_GAME = new Game(SPEED_PENTE, "Speed Pente", true);
    public static final Game TB_PENTE_GAME = new Game(TB_PENTE, "Pente", false);
    public static final Game KERYO_GAME = new Game(KERYO, "Keryo-Pente", false);
    public static final Game SPEED_KERYO_GAME = new Game(SPEED_KERYO, "Speed Keryo-Pente", true);
    public static final Game TB_KERYO_GAME = new Game(TB_KERYO, "Keryo-Pente", false);
    public static final Game GOMOKU_GAME = new Game(GOMOKU, "Gomoku", false);
    public static final Game SPEED_GOMOKU_GAME = new Game(SPEED_GOMOKU, "Speed Gomoku", true);
    public static final Game TB_GOMOKU_GAME = new Game(TB_GOMOKU, "Gomoku", false);
    public static final Game DPENTE_GAME = new Game(DPENTE, "D-Pente", false);
    public static final Game SPEED_DPENTE_GAME = new Game(SPEED_DPENTE, "Speed D-Pente", true);
    public static final Game TB_DPENTE_GAME = new Game(TB_DPENTE, "D-Pente", false);
    public static final Game GPENTE_GAME = new Game(GPENTE, "G-Pente", false);
    public static final Game SPEED_GPENTE_GAME = new Game(SPEED_GPENTE, "Speed G-Pente", true);
    public static final Game TB_GPENTE_GAME = new Game(TB_GPENTE, "G-Pente", false);
    public static final Game POOF_PENTE_GAME = new Game(POOF_PENTE, "Poof-Pente", false);
    public static final Game SPEED_POOF_PENTE_GAME = new Game(SPEED_POOF_PENTE, "Speed Poof-Pente", true);
    public static final Game TB_POOF_PENTE_GAME = new Game(TB_POOF_PENTE, "Poof-Pente", false);
    public static final Game CONNECT6_GAME = new Game(CONNECT6, "Connect6", false);
    public static final Game SPEED_CONNECT6_GAME = new Game(SPEED_CONNECT6, "Speed Connect6", true);
    public static final Game TB_CONNECT6_GAME = new Game(TB_CONNECT6, "Connect6", false);
    public static final Game BOAT_PENTE_GAME = new Game(BOAT_PENTE, "Boat-Pente", false);
    public static final Game SPEED_BOAT_PENTE_GAME = new Game(SPEED_BOAT_PENTE, "Speed Boat-Pente", true);
    public static final Game TB_BOAT_PENTE_GAME = new Game(TB_BOAT_PENTE, "Boat-Pente", false);
    public static final Game DKERYO_GAME = new Game(DKERYO, "DK-Pente", false);
    public static final Game SPEED_DKERYO_GAME = new Game(SPEED_DKERYO, "Speed DK-Pente", true);
    public static final Game TB_DKERYO_GAME = new Game(TB_DKERYO, "DK-Pente", false);

    public static final Game GO_GAME = new Game(GO, "Go", false);
    public static final Game SPEED_GO_GAME = new Game(SPEED_GO, "Speed Go", true);
    public static final Game TB_GO_GAME = new Game(TB_GO, "Go", false);
    public static final Game GO9_GAME = new Game(GO9, "Go (9x9)", false);
    public static final Game SPEED_GO9_GAME = new Game(SPEED_GO9, "Speed Go (9x9)", true);
    public static final Game TB_GO9_GAME = new Game(TB_GO9, "Go (9x9)", false);
    public static final Game GO13_GAME = new Game(GO13, "Go (13x13)", false);
    public static final Game SPEED_GO13_GAME = new Game(SPEED_GO13, "Speed Go (13x13)", true);
    public static final Game TB_GO13_GAME = new Game(TB_GO13, "Go (13x13)", false);

    public static final Game OPENTE_GAME = new Game(OPENTE, "O-Pente", false);
    public static final Game SPEED_OPENTE_GAME = new Game(SPEED_OPENTE, "Speed O-Pente", true);
    public static final Game TB_OPENTE_GAME = new Game(TB_OPENTE, "O-Pente", false);
    public static final Game SWAP2PENTE_GAME = new Game(SWAP2PENTE, "Swap2-Pente", false);
    public static final Game SPEED_SWAP2PENTE_GAME = new Game(SPEED_SWAP2PENTE, "Speed Swap2-Pente", true);
    public static final Game TB_SWAP2PENTE_GAME = new Game(TB_SWAP2PENTE, "Swap2-Pente", false);
    public static final Game SWAP2KERYO_GAME = new Game(SWAP2KERYO, "Swap2-Keryo", false);
    public static final Game SPEED_SWAP2KERYO_GAME = new Game(SPEED_SWAP2KERYO, "Speed Swap2-Keryo", true);
    public static final Game TB_SWAP2KERYO_GAME = new Game(TB_SWAP2KERYO, "Swap2-Keryo", false);
    public static final Game RENJU_GAME = new Game(RENJU, "Renju", false);
    public static final Game SPEED_RENJU_GAME = new Game(SPEED_RENJU, "Speed Renju", true);
    public static final Game TB_RENJU_GAME = new Game(TB_RENJU, "Renju", false);

    private static final Game allGames[] = {
            null, PENTE_GAME, SPEED_PENTE_GAME, KERYO_GAME, SPEED_KERYO_GAME,
            GOMOKU_GAME, SPEED_GOMOKU_GAME, DPENTE_GAME, SPEED_DPENTE_GAME,
            GPENTE_GAME, SPEED_GPENTE_GAME, POOF_PENTE_GAME, SPEED_POOF_PENTE_GAME,
            CONNECT6_GAME, SPEED_CONNECT6_GAME, BOAT_PENTE_GAME, SPEED_BOAT_PENTE_GAME,
            DKERYO_GAME, SPEED_DKERYO_GAME, GO_GAME, SPEED_GO_GAME,
            GO9_GAME, SPEED_GO9_GAME, GO13_GAME, SPEED_GO13_GAME,
            OPENTE_GAME, SPEED_OPENTE_GAME, SWAP2PENTE_GAME, SPEED_SWAP2PENTE_GAME,
            SWAP2KERYO_GAME, SPEED_SWAP2KERYO_GAME,
            RENJU_GAME, SPEED_RENJU_GAME,
            TB_PENTE_GAME, TB_KERYO_GAME, TB_GOMOKU_GAME, TB_DPENTE_GAME,
            TB_GPENTE_GAME, TB_POOF_PENTE_GAME, TB_CONNECT6_GAME,
            TB_BOAT_PENTE_GAME, TB_DKERYO_GAME, TB_GO_GAME,
            TB_GO9_GAME, TB_GO13_GAME, TB_OPENTE_GAME, TB_SWAP2PENTE_GAME, TB_SWAP2KERYO_GAME,
            TB_RENJU_GAME
    };
    private static final Game displaygames[] = {
            PENTE_GAME,
            KERYO_GAME,
            GOMOKU_GAME,
            CONNECT6_GAME,
            BOAT_PENTE_GAME,
            DPENTE_GAME,
            GPENTE_GAME,
            POOF_PENTE_GAME,
            DKERYO_GAME,
            GO_GAME,
            GO9_GAME,
            GO13_GAME,
            OPENTE_GAME,
            SWAP2PENTE_GAME,
            SWAP2KERYO_GAME,
            RENJU_GAME,
            new Game(TB_PENTE, "Turn-based Pente", false),
            new Game(TB_KERYO, "Turn-based Keryo-Pente", false),
            new Game(TB_GOMOKU, "Turn-based Gomoku", false),
            new Game(TB_CONNECT6, "Turn-based Connect6", false),
            new Game(TB_BOAT_PENTE, "Turn-based Boat-Pente", false),
            new Game(TB_DPENTE, "Turn-based D-Pente", false),
            new Game(TB_GPENTE, "Turn-based G-Pente", false),
            new Game(TB_POOF_PENTE, "Turn-based Poof-Pente", false),
            new Game(TB_DKERYO, "Turn-based DK-Pente", false),
            new Game(TB_GO, "Turn-based Go", false),
            new Game(TB_GO9, "Turn-based Go (9x9)", false),
            new Game(TB_GO13, "Turn-based Go (13x13)", false),
            new Game(TB_OPENTE, "Turn-based O-Pente", false),
            new Game(TB_SWAP2PENTE, "Turn-based Swap2-Pente", false),
            new Game(TB_SWAP2KERYO, "Turn-based Swap2-Keryo", false),
            new Game(TB_RENJU, "Turn-based Renju", false),
            SPEED_PENTE_GAME,
            SPEED_KERYO_GAME,
            SPEED_GOMOKU_GAME,
            SPEED_CONNECT6_GAME,
            SPEED_BOAT_PENTE_GAME,
            SPEED_DPENTE_GAME,
            SPEED_GPENTE_GAME,
            SPEED_POOF_PENTE_GAME,
            SPEED_DKERYO_GAME,
            SPEED_GO_GAME,
            SPEED_GO9_GAME,
            SPEED_GO13_GAME,
            SPEED_OPENTE_GAME,
            SPEED_SWAP2PENTE_GAME,
            SPEED_SWAP2KERYO_GAME,
            SPEED_RENJU_GAME
    };

    private static final Game normalGames[] = {
            PENTE_GAME, KERYO_GAME,
            GOMOKU_GAME, DPENTE_GAME,
            GPENTE_GAME, POOF_PENTE_GAME,
            CONNECT6_GAME, BOAT_PENTE_GAME,
            DKERYO_GAME,
            GO_GAME, GO9_GAME, GO13_GAME,
            OPENTE_GAME, SWAP2PENTE_GAME, SWAP2KERYO_GAME,
            RENJU_GAME
    };
    private static final Game speedGames[] = {
            SPEED_PENTE_GAME, SPEED_KERYO_GAME,
            SPEED_GOMOKU_GAME, SPEED_DPENTE_GAME,
            SPEED_GPENTE_GAME, SPEED_POOF_PENTE_GAME,
            SPEED_CONNECT6_GAME, SPEED_BOAT_PENTE_GAME,
            SPEED_DKERYO_GAME,
            SPEED_GO_GAME, SPEED_GO9_GAME, SPEED_GO13_GAME,
            SPEED_OPENTE_GAME, SPEED_SWAP2PENTE_GAME, SPEED_SWAP2KERYO_GAME,
            SPEED_RENJU_GAME
    };
    private static final Game tbGames[] = {
            TB_PENTE_GAME, TB_KERYO_GAME,
            TB_GOMOKU_GAME, TB_DPENTE_GAME,
            TB_GPENTE_GAME, TB_POOF_PENTE_GAME,
            TB_CONNECT6_GAME, TB_BOAT_PENTE_GAME,
            TB_DKERYO_GAME,
            TB_GO_GAME, TB_GO9_GAME, TB_GO13_GAME,
            TB_OPENTE_GAME, TB_SWAP2PENTE_GAME, TB_SWAP2KERYO_GAME,
            TB_RENJU_GAME
    };

    private static final GridState gridStates[] = new GridState[getNumGames() + 1];
    private static final GridState tbGridStates[] = new GridState[tbGames.length];

    static {
        for (int i = 1; i < gridStates.length; i++) {
            gridStates[i] = createGridState(i);
        }
        for (int i = 0; i < tbGridStates.length; i++) {
            tbGridStates[i] = createGridState(tbGames[i].getId());
        }
    }

    /**
     * Prevent instantiation
     */
    private GridStateFactory() {
    }

    public static GridState createGridState(int game) {
        return createGridState(game, 19, 19);
    }

    public static GridState createGridState(int game, int x, int y) {
        SimpleGomokuState gomoku = new SimpleGomokuState(x, y);
        gomoku.setDoHashes(false);
        switch (game) {
            case PENTE:
            case SPEED_PENTE:
            case TB_PENTE:
                gomoku.allowOverlines(true);
                PenteState penteState = new SimplePenteState(gomoku);
                penteState.setTournamentRule(true);
                return penteState;
            case KERYO:
            case SPEED_KERYO:
            case TB_KERYO:
                gomoku.allowOverlines(true);
                PenteState keryoState = new SimplePenteState(gomoku);
                keryoState.setTournamentRule(true);
                keryoState.setCaptureLengths(new int[]{2, 3});
                keryoState.setCapturesToWin(15);
                return keryoState;
            case GOMOKU:
            case SPEED_GOMOKU:
            case TB_GOMOKU:
                gomoku.setDoHashes(true);
                gomoku.allowOverlines(false);
                return gomoku;
            case GPENTE:
            case SPEED_GPENTE:
            case TB_GPENTE:
                gomoku.allowOverlines(true);
                PenteState gpenteState = new SimplePenteState(gomoku);
                gpenteState.setTournamentRule(true);
                gpenteState.setGPenteRules(true);
                return gpenteState;
            case POOF_PENTE:
            case SPEED_POOF_PENTE:
            case TB_POOF_PENTE:
                gomoku.allowOverlines(true);
                PenteState poofState = new SimplePoofPenteState(gomoku);
                poofState.setGPenteRules(false);
                poofState.setTournamentRule(true);
                return poofState;
            case DPENTE:
            case SPEED_DPENTE:
            case TB_DPENTE:
                gomoku.allowOverlines(true);
                PenteState dpenteState = new SimplePenteState(gomoku);
                dpenteState.setTournamentRule(false);
                dpenteState.setDPenteRules(true);
                return dpenteState;
            case CONNECT6:
            case SPEED_CONNECT6:
            case TB_CONNECT6:
                return new SimpleConnect6State(x, y);
            case BOAT_PENTE:
            case SPEED_BOAT_PENTE:
            case TB_BOAT_PENTE:
                gomoku.allowOverlines(true);
                return new BoatPenteState(gomoku);
            case DKERYO:
            case SPEED_DKERYO:
            case TB_DKERYO:
                gomoku.allowOverlines(true);
                PenteState dkeryoState = new SimplePenteState(gomoku);
                dkeryoState.setTournamentRule(false);
                dkeryoState.setDPenteRules(true);
                dkeryoState.setCaptureLengths(new int[]{2, 3});
                dkeryoState.setCapturesToWin(15);
                return dkeryoState;
            case GO:
            case SPEED_GO:
            case TB_GO:
                GoState goState = new GoState(x, y);
                return goState;
            case GO9:
            case SPEED_GO9:
            case TB_GO9:
                GoState go9State = new GoState(9, 9);
                return go9State;
            case GO13:
            case SPEED_GO13:
            case TB_GO13:
                GoState go13State = new GoState(13, 13);
                return go13State;
            case OPENTE:
            case SPEED_OPENTE:
            case TB_OPENTE:
                gomoku.allowOverlines(true);
                PenteState oPenteState = new OPenteState(gomoku);
                oPenteState.setTournamentRule(true);
                oPenteState.setCaptureLengths(new int[]{2, 3});
                oPenteState.setCapturesToWin(15);
                return oPenteState;
            case SWAP2PENTE:
            case SPEED_SWAP2PENTE:
            case TB_SWAP2PENTE:
                gomoku.allowOverlines(true);
                PenteState swap2PenteState = new SimplePenteState(gomoku);
                swap2PenteState.setTournamentRule(false);
                swap2PenteState.setCaptureLengths(new int[]{2});
                swap2PenteState.setCapturesToWin(10);
                swap2PenteState.setSwap2Rules(true);
                return swap2PenteState;
            case SWAP2KERYO:
            case SPEED_SWAP2KERYO:
            case TB_SWAP2KERYO:
                gomoku.allowOverlines(true);
                PenteState swap2KeryoState = new SimplePenteState(gomoku);
                swap2KeryoState.setTournamentRule(false);
                swap2KeryoState.setCaptureLengths(new int[]{2, 3});
                swap2KeryoState.setCapturesToWin(15);
                swap2KeryoState.setSwap2Rules(true);
                return swap2KeryoState;
            case RENJU:
            case SPEED_RENJU:
            case TB_RENJU:
                // Renju is played on the canonical 15x15 board, independent of
                // the requested size (like GO9/GO13). RenjuState configures its
                // own wrapped gomoku (overlines on, win rules + Taraguchi-10).
                return new RenjuState(15, 15);
        }

        return null;
    }

    public static GridState createGridState(int game, MoveData moveData) {
        if (game > TB_START) {
            return tbGridStates[(game - TB_START - 1) / 2].getInstance(moveData);
        } else {
            return gridStates[game].getInstance(moveData);
        }
    }

    public static Game getGame(int game) {
        if (game > TB_START) {
            return tbGames[(game - TB_START - 1) / 2];
        } else {
            return allGames[game];
        }
    }

    public static boolean isValidGame(int game) {
        return game >= PENTE && game <= SPEED_RENJU;
    }

    public static String getGameName(int game) {
        return getGame(game).getName();
    }

    public static int getGameId(String gameName) throws IllegalArgumentException {
        for (int i = 1; i < allGames.length; i++) {
            if (allGames[i].getName().equals(gameName)) {
                return allGames[i].getId();
            }
        }
        for (Game tbGame : tbGames) {
            if (tbGame.getName().equals(gameName)) {
                return tbGame.getId();
            }
        }
        throw new IllegalArgumentException("Invalid game: " + gameName);
    }

    public static int getNumGames() {
        return allGames.length - 1;
    }

    public static int getMaxGameId() {
        return TB_RENJU;
    }

    public static Game[] getAllGames() {
        return allGames;
    }

    public static String getDisplayName(int game) {
        for (Game displaygame : displaygames) {
            if (displaygame.getId() == game) {
                return displaygame.getName();
            }
        }
        return null;
    }

    public static Game[] getDisplayGames() {
        return displaygames;
    }

    public static Game[] getSpeedGames() {
        return speedGames;
    }

    public static Game[] getNormalGames() {
        return normalGames;
    }

    public static Game[] getTbGames() {
        return tbGames;
    }

    public static Game getSpeedGame(Game normalGame) {
        return allGames[normalGame.getId() + 1];
    }

    public static Game getNormalGame(Game speedGame) {
        return allGames[speedGame.getId() - 1];
    }

    public static int getNormalGameFromTurnbased(int game) {
        return game - TB_START;
    }

    public static boolean isSpeedGame(int game) {
        return game < TB_START && (game % 2) == 0;
    }

    public static boolean isTurnbasedGame(int game) {
        return game > TB_START;
    }

    public static int getColor(int moveNum, int game) {
        return gridStates[game].getColor(moveNum);
    }

    /**
     * The board-center move for a game, used as the auto-placed opening stone
     * (tournament/Taraguchi "first stone in the center"). Derived from the
     * game's actual board size so it is correct for every variant: 19x19 -> 180,
     * Renju 15x15 -> 112. Avoids hardcoding the 19x19 center.
     */
    public static int getCenterMove(int game) {
        GridState gs = createGridState(game);
        int cx = gs.getGridSizeX() / 2;
        int cy = gs.getGridSizeY() / 2;
        return gs.convertMove(cx, cy);
    }

    /**
     * True for games whose tournament sets are a single game rather than a
     * two-game color-alternating pair. The go family balances first-move
     * advantage via komi; renju (Taraguchi-10) balances it via the opening
     * swap protocol. Both therefore play one game per tournament set.
     */
    public static boolean isSingleGameSet(int game) {
        return game == GO || game == GO9 || game == GO13 ||
                game == SPEED_GO || game == SPEED_GO9 || game == SPEED_GO13 ||
                game == TB_GO || game == TB_GO9 || game == TB_GO13 ||
                game == RENJU || game == SPEED_RENJU || game == TB_RENJU;
    }

}