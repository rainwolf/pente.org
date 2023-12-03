package org.pente.turnBased.web;

import java.io.*;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.pente.game.*;
import org.pente.gameServer.tourney.TourneyStorer;
import org.pente.notifications.NotificationServer;
import org.pente.turnBased.*;
import org.pente.gameServer.core.*;
import org.pente.gameServer.server.*;

import org.apache.log4j.*;

public class MoveServlet extends HttpServlet {

    private static final Category log4j = Category.getInstance(
            MoveServlet.class.getName());

    private static final String gamePage = "/gameServer/tb/mobileGame.jsp";
    private static final String mobileGamePage = "/gameServer/tb/mobileGame.jsp";
    private static final String errorRedirectPage = "/gameServer/tb/error.jsp";
    private static final String moveRedirectPage = "/gameServer/index.jsp";
    private static final String cancelRedirectPage = "/gameServer/tb/cancelReply.jsp";
    private static final String undoRedirectPage = "/gameServer/tb/undoReply.jsp";
    private static final String mobileRedirectPage = "/gameServer/mobile/empty.jsp";

    private static final String goMarkDeadRedirectPage = "/gameServer/tb/deadGo.jsp";
    private static final String goFinalStepRedirectPage = "/gameServer/tb/finalGo.jsp";

    private Resources resources;

    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        ServletContext ctx = config.getServletContext();
        resources = (Resources) ctx.getAttribute(Resources.class.getName());
    }

    // expected params:
    // player - required (user logged in so will be there)
    // gid - required
    // command - load, move - required
    // gowhere - optional
    // message - optional
    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
            throws ServletException, IOException {
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request,
                       HttpServletResponse response)
            throws ServletException, IOException {

        String error = null;

        DSGPlayerStorer dsgPlayerStorer = resources.getDsgPlayerStorer();
        TBGameStorer tbGameStorer = resources.getTbGameStorer();

        String player = (String) request.getAttribute("name");
        DSGPlayerData playerData = null;

        String gidStr = (String) request.getParameter("gid");
        long gid = 0;
        TBGame game = null;
        TBSet set = null;
        String command = (String) request.getParameter("command");

        String attachStr = request.getParameter("attach");

        if (command == null) {
            log4j.error("MoveServlet, invalid command");
            handleError(request, response, "Invalid command.");
            return;
        }
        log4j.debug("MoveServlet, command: " + command);

        // player must be logged in, so name will be populated
        try {
            playerData = dsgPlayerStorer.loadPlayer(player);
        } catch (DSGPlayerStoreException e) {
            log4j.error("MoveServlet, problem loading player " + player, e);
            handleError(request, response,
                    "Database error, please try again later.");
            return;
        }

        if (gidStr != null) {
            try {
                gid = Long.parseLong(gidStr);
            } catch (NumberFormatException nef) {
            }
        }
        if (gid == 0) {
            log4j.error("MoveServlet, invalid gid " + gidStr);
            handleError(request, response, "No game or invalid game.");
            return;
        }
        try {
            game = tbGameStorer.loadGame(gid);
            set = game.getTbSet();
            if (game == null || set == null) {
                log4j.error("MoveServlet, invalid game, storer returned null " + gid);
                handleError(request, response, "Game not found.");
                return;
            }

            boolean isSwap2 = game.getGame() == GridStateFactory.TB_SWAP2PENTE ||
                    game.getGame() == GridStateFactory.TB_SWAP2KERYO;

            request.setAttribute("game", game);

            try {
                DSGPlayerData p1 = dsgPlayerStorer.loadPlayer(game.getPlayer1Pid());
                DSGPlayerData p2 = dsgPlayerStorer.loadPlayer(game.getPlayer2Pid());
                request.setAttribute("p1", p1);
                request.setAttribute("p2", p2);

            } catch (DSGPlayerStoreException dpse) {
                log4j.error("MoveServlet, error loading player data", dpse);
                handleError(request, response, "Database error");
                return;
            }

            if (command.equals("load")) {
                if (game.getState() == TBGame.STATE_ACTIVE &&
                        game.getCurrentPlayer() == playerData.getPlayerID()) {
                    request.setAttribute("myTurn", "true");
                }
                if (playerData.getPlayerID() != game.getPlayer1Pid() &&
                        playerData.getPlayerID() != game.getPlayer2Pid()) {

                    if (game.getTbSet().isPrivateGame() || game.isHidden()) {
                        log4j.error("MoveServlet, trying to view hidden or private game");
                        handleError(request, response, "Invalid game, game hidden or private and other player trying to view it.");
                        return;
                    }

//					if (!playerData.isAdmin() && 
//						game.getState() == TBGame.STATE_ACTIVE) {
//						TourneyStorer tourneyStorer = resources.getTourneyStorer();
//						int eventId = game.getEventId();
//						if (tourneyStorer.getTourney(eventId) == null) {
//							log4j.error("MoveServlet, game state invalid " + gid);
//							handleError(request, response, "Invalid game, game active and other player trying to view it.");
//							return;
//						}
//					}

                    // else if complete, show game but restrict messages
                    request.setAttribute("showMessages", new Boolean(playerData.isAdmin()));
                }

                // if someone requested a cancel for the set
                // and this game is active
                // and the requestor is not this player
                if (set.getCancelPid() != 0 &&
                        game.getState() == TBGame.STATE_ACTIVE &&
                        set.getCancelPid() != playerData.getPlayerID() &&
                        (playerData.getPlayerID() == game.getPlayer1Pid() ||
                                playerData.getPlayerID() == game.getPlayer2Pid())) {
                    log4j.debug("forward to cancel reply page");

                    request.setAttribute("set", set);
                    getServletContext().getRequestDispatcher(cancelRedirectPage).forward(
                            request, response);
                    return;
                }

                if (game.isUndoRequested() && game.getState() == TBGame.STATE_ACTIVE &&
                        (playerData.getPlayerID() == game.getCurrentPlayer())) {
                    log4j.debug("forward to undo reply page");

                    request.setAttribute("set", set);
                    getServletContext().getRequestDispatcher(undoRedirectPage).forward(
                            request, response);
                    return;
                }


                if (game.getGame() == GridStateFactory.TB_GO ||
                        game.getGame() == GridStateFactory.TB_GO9 ||
                        game.getGame() == GridStateFactory.TB_GO13) {
                    if (game.getGoState() == TBGame.GO_MARK_DEAD_STONES) {
                        log4j.debug("forward to Go mark dead stones page");

                        request.setAttribute("set", set);
                        getServletContext().getRequestDispatcher(goMarkDeadRedirectPage).forward(
                                request, response);
                        return;
                    } else if (game.getGoState() == TBGame.GO_EVALUATE_DEAD_STONES) {
                        log4j.debug("forward to Go final page");

                        request.setAttribute("set", set);
                        getServletContext().getRequestDispatcher(goFinalStepRedirectPage).forward(
                                request, response);
                        return;
                    }
                }

                // if player prefers to make moves attached or not
                List prefs = dsgPlayerStorer.loadPlayerPreferences(
                        playerData.getPlayerID());
                for (Iterator it = prefs.iterator(); it.hasNext(); ) {
                    DSGPlayerPreference p = (DSGPlayerPreference) it.next();
                    if (p.getName().equals("attach")) {
                        request.setAttribute("attach", p.getValue());
                    }
                }


                log4j.debug("forward to game page");
                String isMobile = (String) request.getParameter("mobile");
                if (isMobile == null) {
                    getServletContext().getRequestDispatcher(gamePage).forward(
                            request, response);
                } else {
                    getServletContext().getRequestDispatcher(mobileGamePage).forward(
                            request, response);
                }
                log4j.debug("done forwarding");
                return;
            } else if (command.equals("requestUndo")) {
                DSGPlayerData plr = dsgPlayerStorer.loadPlayer(game.getOpponent(game.getCurrentPlayer()));
                if (game.getOpponent(game.getCurrentPlayer()) != playerData.getPlayerID()) {
                    log4j.error("MoveServlet, out-of-turn undo request");
                    handleError(request, response,
                            "Undo request is available when it's not your turn.");
                    return;
                }
                if (plr.hasPlayerDonated()) {
                    if ((game.getGame() == GridStateFactory.TB_DPENTE || game.getGame() == GridStateFactory.TB_DKERYO) && game.getNumMoves() < 5) {
                        log4j.error("MoveServlet, undo request for d-pente or dk-pente opening");
                        handleError(request, response,
                                "Undo requests are not available for opening moves.");
                        return;
                    } else if (isSwap2 && game.getNumMoves() < 6) {
                        log4j.error("MoveServlet, undo request for swap2 opening");
                        handleError(request, response,
                                "Undo requests are not available for opening moves.");
                        return;
                    } else if (game.getNumMoves() == 1) {
                        log4j.error("MoveServlet, undo request no move");
                        handleError(request, response,
                                "Undo requests are possible after the 1st move is played.");
                        return;
                    }
                    ((CacheTBStorer) tbGameStorer).requestUndo(gid);
                } else {
                    log4j.error("MoveServlet, undo request for non-subscriber ");
                    handleError(request, response,
                            "Undo requests are available to subscribers only.");
                    return;
                }
                if (game.getOpponent(game.getCurrentPlayer()) != playerData.getPlayerID()) {
                    log4j.error("MoveServlet, out-of-turn undo request");
                    handleError(request, response,
                            "Undo requests are available when it's not your turn.");
                    return;
                }
                if (game.getNumMoves() == 0) {
                    log4j.error("MoveServlet, nothing to undo");
                    handleError(request, response,
                            "No moves played yet, nothing to request undo for.");
                    return;
                }
                log4j.debug("forward to game page");
                String isMobile = (String) request.getParameter("mobile");
                if (isMobile == null) {
                    response.sendRedirect(moveRedirectPage);
                } else {
                    response.sendRedirect(mobileRedirectPage);
                }
                log4j.debug("done forwarding");
                return;
            } else if (command.equals("acceptUndo")) {
                if (game.getCurrentPlayer() != playerData.getPlayerID()) {
                    log4j.error("MoveServlet, out-of-turn undo accept");
                    handleError(request, response,
                            "Undo accept is available when it's your turn.");
                    return;
                }
                if (!game.isUndoRequested()) {
                    log4j.error("MoveServlet, no undo request exists");
                    handleError(request, response,
                            "No undo request exists.");
                    return;
                }
                if (game.getNumMoves() > 1) {
                    int numMoves = 1;
                    if (game.getGame() == GridStateFactory.TB_CONNECT6) {
                        numMoves += 1;
                    }
                    ((CacheTBStorer) tbGameStorer).undoLastMove(gid, numMoves);
                    NotificationServer notificationServer = resources.getNotificationServer();
                    notificationServer.sendMoveNotification(playerData.getName(), game.getCurrentPlayer(), game.getGid(), GridStateFactory.getGameName(game.getGame()));
                }

                log4j.debug("forward to game page");
                String isMobile = (String) request.getParameter("mobile");
                if (isMobile == null) {
                    response.sendRedirect(moveRedirectPage);
                } else {
                    response.sendRedirect(mobileRedirectPage);
                }
                log4j.debug("done forwarding");
                return;
            } else if (command.equals("declineUndo")) {
                if (game.getCurrentPlayer() != playerData.getPlayerID()) {
                    log4j.error("MoveServlet, out-of-turn undo decline");
                    handleError(request, response,
                            "Undo decline is available when it's your turn.");
                    return;
                }
                if (!game.isUndoRequested()) {
                    log4j.error("MoveServlet, no undo request exists");
                    handleError(request, response,
                            "No undo request exists.");
                    return;
                }
                ((CacheTBStorer) tbGameStorer).declineUndo(gid);

                if (game.getState() == TBGame.STATE_ACTIVE &&
                        game.getCurrentPlayer() == playerData.getPlayerID()) {
                    request.setAttribute("myTurn", "true");
                }

                log4j.debug("forward to game page");
                String isMobile = (String) request.getParameter("mobile");
                if (isMobile == null) {
                    getServletContext().getRequestDispatcher(gamePage).forward(
                            request, response);
                } else {
                    getServletContext().getRequestDispatcher(mobileGamePage).forward(
                            request, response);
                }
                log4j.debug("done forwarding");
                return;
            } else if (command.equals("move")) {

// log4j.debug("************current player initial pid " + game.getCurrentPlayer());

                if (game.getCurrentPlayer() != playerData.getPlayerID()) {
                    log4j.debug("MoveServlet, " + playerData.getName() + "" +
                            "attempted to make move out of turn: " + game.getGid());
                    handleError(request, response, "Its not your turn.");
                    return;
                }
                if (game.getState() != TBGame.STATE_ACTIVE) {
                    log4j.error("MoveServlet, game state invalid " + gid);
                    handleError(request, response, "Invalid game, game not active.");
                    return;
                }

                // handle dpente stuff here, underlying code doesn't need
                // to change

                // load moves
                int moves[] = null;
                String moveStr = request.getParameter("moves");
                if ((moveStr != null) && !"(null)".equals(moveStr)) {
                    try {
                        StringTokenizer st = new StringTokenizer(moveStr, ",");
                        moves = new int[st.countTokens()];
                        for (int i = 0; i < moves.length; i++) {
                            moves[i] = Integer.parseInt(st.nextToken());
                        }
                    } catch (NumberFormatException nfe) {
                    }
                }
                if (moves == null) {
                    log4j.error("MoveServlet, invalid moves " + moveStr + ": " +
                            game.getGid());
                    handleError(request, response, "Invalid moves.");
                    return;
                }

                String msg = request.getParameter("message");
                TBMessage message = null;
                if (msg != null && !msg.trim().equals("")) {
                    message = new TBMessage();
                    message.setMessage(msg.substring(0, Math.min(msg.length(), 255)));
                    message.setDate(new java.util.Date());
                    message.setMoveNum(game.getNumMoves() + 1);
                    // default seq nbr = 1
                    message.setSeqNbr(1);
                    message.setPid(game.getCurrentPlayer());
                }

                String hideStr = request.getParameter("hide");
//                if (hideStr != null && (game.getEventId() == tbGameStorer.getEventId(game.getGame()))) {
                if (hideStr != null) {
                    if (playerData.hasPlayerDonated()) {
                        byte hiddenBy = 0;
                        if ("yes".equals(hideStr)) {
                            hiddenBy = (byte) (playerData.getPlayerID() == game.getPlayer1Pid() ? 1 : 2);
                        }
                        tbGameStorer.hideGame(game.getGid(), hiddenBy);
                    }
                }

                // handle dpente separately
                if ((game.getGame() == GridStateFactory.TB_DPENTE || game.getGame() == GridStateFactory.TB_DKERYO) &&
                        game.getDPenteState() != TBGame.DPENTE_STATE_DECIDED) {

                    log4j.debug("MoveServlet, handle dpente move");
                    if (game.getDPenteState() == TBGame.DPENTE_STATE_START) {
                        if (moves.length != 4) {
                            log4j.error("MoveServlet, dpente game start, " +
                                    "expected 4 moves.");
                            handleError(request, response, "Expected 4 moves.");
                            return;
                        }
                        if ((moves[0] == moves[1] || moves[0] == moves[2] || moves[0] == moves[3]
                                || moves[2] == moves[1] || moves[1] == moves[3] || moves[2] == moves[3]) ||
                                (moves[0] < 0 || moves[0] > 360) ||
                                (moves[1] < 0 || moves[1] > 360) ||
                                (moves[3] < 0 || moves[3] > 360) ||
                                (moves[2] < 0 || moves[2] > 360)) {
                            log4j.error("MoveServlet, d(keryo)pente game start, " +
                                    "expected 4 different moves.");
                            handleError(request, response, "Expected 4 different moves to start d(keryo)pente.");
                            return;
                        }
                        log4j.debug("MoveServlet, handle d(keryo)pente start");

                        tbGameStorer.updateDPenteState(game, TBGame.DPENTE_STATE_DECIDE);

                        for (int i = 0; i < moves.length; i++) {
                            tbGameStorer.storeNewMove(game.getGid(), game.getNumMoves(),
                                    moves[i]);
                        }

                        if (message != null) {
                            message.setMoveNum(4);
                            tbGameStorer.storeNewMessage(game.getGid(), message);
                        }
                    } else if (game.getDPenteState() == TBGame.DPENTE_STATE_DECIDE) {

                        log4j.debug("MoveServlet, handle dpente decision");

                        boolean swap = moves[0] == 1;
                        tbGameStorer.dPenteSwap(game, swap);

                        // didn't swap but still might have written message
                        if (!swap && message != null) {
                            // set seq nbr
                            log4j.debug("MoveServlet, no swap record message");
                            message.setMoveNum(4);
                            message.setSeqNbr(2);
                            tbGameStorer.storeNewMessage(game.getGid(), message);
                        } else if (swap) {
                            log4j.debug("MoveServlet, swap, " + moves[1]);
                            game.setDPenteSwapped(true);
                            tbGameStorer.storeNewMove(game.getGid(), game.getNumMoves(),
                                    moves[1]);
                            if (game.isHidden()) {
                                tbGameStorer.hideGame(game.getGid(), (byte) (3 - game.getHiddenBy()));
                            }
                            if (message != null) {
                                message.setMoveNum(5);
                                tbGameStorer.storeNewMessage(game.getGid(), message);
                            }
                        }

                    }
                } else if (isSwap2 && game.getDPenteState() != TBGame.DPENTE_STATE_DECIDED) {

                    log4j.debug("MoveServlet, handle swap2 move");
                    if (game.getDPenteState() == TBGame.DPENTE_STATE_START) {
                        if (moves.length != 3) {
                            log4j.error("MoveServlet, dpente game start, expected 3 moves.");
                            handleError(request, response, "Expected 3 moves.");
                            return;
                        }
                        if ((moves[0] == moves[1] || moves[0] == moves[2] || moves[2] == moves[1]) ||
                                (moves[0] < 0 || moves[0] > 360) || (moves[1] < 0 || moves[1] > 360) ||
                                (moves[2] < 0 || moves[2] > 360)) {
                            log4j.error("MoveServlet, swap2 game start, expected different moves.");
                            handleError(request, response, "Expected different moves to start swap2.");
                            return;
                        }
                        log4j.debug("MoveServlet, handle swap2 start");

                        tbGameStorer.updateDPenteState(game, TBGame.DPENTE_STATE_DECIDE);

                        for (int move : moves) {
                            tbGameStorer.storeNewMove(game.getGid(), game.getNumMoves(), move);
                        }

                        if (message != null) {
                            message.setMoveNum(4);
                            tbGameStorer.storeNewMessage(game.getGid(), message);
                        }
                    } else if (game.getDPenteState() == TBGame.DPENTE_STATE_DECIDE &&
                            (game.getNumMoves() == 3 || game.getNumMoves() == 5)) {
                        log4j.debug("MoveServlet, handle swap2 decision at move " + game.getNumMoves());
                        boolean pass = moves[0] == 2 && game.getNumMoves() == 3;
                        // p2 wants to play as p1, or p1 wants to play as p2
                        boolean swap = (moves[0] == 0 && game.getNumMoves() == 3) ||
                                (moves[0] == 1 && game.getNumMoves() == 5);
                        boolean addOneMove = moves[0] == 1;
                        if (!pass) {
                            tbGameStorer.updateDPenteState(game, TBGame.DPENTE_STATE_DECIDE);
                            tbGameStorer.dPenteSwap(game, swap);
                        }

                        // didn't swap but still might have written message
                        if (pass) {
                            game.setSwap2Pass(true);
                            tbGameStorer.swap2Pass(game);

                            int numMoves = game.getNumMoves();
                            tbGameStorer.storeNewMove(game.getGid(), numMoves, moves[1]);
                            tbGameStorer.storeNewMove(game.getGid(), numMoves + 1, moves[2]);
                            if (game.isHidden()) {
                                tbGameStorer.hideGame(game.getGid(), (byte) (3 - game.getHiddenBy()));
                            }
                            if (message != null) {
                                message.setMoveNum(5);
                                tbGameStorer.storeNewMessage(game.getGid(), message);
                            }
                        } else if (!addOneMove && message != null) {
                            // set seq nbr
                            log4j.debug("MoveServlet, swap2 swap, record message");
                            message.setMoveNum(game.getNumMoves());
                            message.setSeqNbr(2);
                            tbGameStorer.storeNewMessage(game.getGid(), message);
                        } else if (addOneMove) {
                            log4j.debug("MoveServlet, swap2 add move " + moves[1]);
                            if (message != null) {
                                message.setMoveNum(game.getNumMoves() + 1);
                                tbGameStorer.storeNewMessage(game.getGid(), message);
                            }
                            tbGameStorer.storeNewMove(game.getGid(), game.getNumMoves(), moves[1]);
                        }
                        if (swap && game.isHidden()) {
                            tbGameStorer.hideGame(game.getGid(), (byte) (3 - game.getHiddenBy()));
                        }
                    }
                } else if (game.getGame() == GridStateFactory.TB_CONNECT6) {
                    log4j.debug("MoveServlet, store moves " + moves[0] + "," + moves[1]);
                    if (moves.length != 2) {
                        log4j.error("MoveServlet, more moves received than, " +
                                "expected.");
                        handleError(request, response, "Invalid move.");
                        return;
                    }
                    if (moves[0] == moves[1]) {
                        log4j.error("MoveServlet, Identical Connect6 moves received");
                        handleError(request, response, "Invalid move.");
                        return;
                    }
                    tbGameStorer.storeNewMove(game.getGid(), game.getNumMoves(),
                            moves[0]);
                    // this will not add the 2nd move if the player
                    // won the game on the 1st move
                    if (!game.isCompleted()) {
                        tbGameStorer.storeNewMove(game.getGid(), game.getNumMoves(),
                                moves[1]);
                    }
                    if (message != null) {
                        message.setMoveNum(game.getNumMoves());
                        tbGameStorer.storeNewMessage(game.getGid(), message);
                    }
                } else if ((game.getGame() == GridStateFactory.TB_GO ||
                        game.getGame() == GridStateFactory.TB_GO9 ||
                        game.getGame() == GridStateFactory.TB_GO13) &&
                        game.getGoState() == TBGame.GO_MARK_DEAD_STONES) {
                    if (moves.length < 1) {
                        log4j.error("MoveServlet, not enough moves GO_MARK_DEAD_STONES for " + game.getGid());
                        handleError(request, response, "Invalid move, not enough moves.");
                        return;
                    }

                    for (int move : moves) {
                        tbGameStorer.storeNewMove(game.getGid(), game.getNumMoves(), move);
                    }

                    if (message != null) {
                        message.setMoveNum(game.getNumMoves());
                        tbGameStorer.storeNewMessage(game.getGid(), message);
                    }

                } else if ((game.getGame() == GridStateFactory.TB_GO ||
                        game.getGame() == GridStateFactory.TB_GO9 ||
                        game.getGame() == GridStateFactory.TB_GO13) &&
                        game.getGoState() == TBGame.GO_EVALUATE_DEAD_STONES) {
                    if (moves.length < 1) {
                        log4j.error("MoveServlet, not enough moves GO_EVALUATE_DEAD_STONES for " + game.getGid());
                        handleError(request, response, "Invalid move, not enough moves.");
                        return;
                    }
                    if (moves[0] == 1) {
                        if (game.getGame() == GridStateFactory.TB_GO) {
                            tbGameStorer.storeNewMove(game.getGid(), game.getNumMoves(), 19 * 19);
                        } else if (game.getGame() == GridStateFactory.TB_GO9) {
                            tbGameStorer.storeNewMove(game.getGid(), game.getNumMoves(), 9 * 9);
                        } else if (game.getGame() == GridStateFactory.TB_GO13) {
                            tbGameStorer.storeNewMove(game.getGid(), game.getNumMoves(), 13 * 13);
                        }
                    } else {
                        ((CacheTBStorer) tbGameStorer).continueGoGame(gid);
                    }
                } else {
                    log4j.debug("MoveServlet, store move " + moves[0]);
                    if (moves.length != 1) {
                        log4j.error("MoveServlet, more moves received than, " +
                                "expected.");
                        handleError(request, response, "Invalid move.");
                        return;
                    }
                    tbGameStorer.storeNewMove(game.getGid(), game.getNumMoves(),
                            moves[0]);
                    if (message != null && !(game.getPlayer1Pid() == 23000000020606L || game.getPlayer2Pid() == 23000000020606L)) {
                        tbGameStorer.storeNewMessage(game.getGid(), message);
                    }
                }


// log4j.debug("************current player pid " + game.getCurrentPlayer());


                NotificationServer notificationServer = resources.getNotificationServer();
                if (!game.isCompleted() || game.getPlayer1Pid() == 23000000020606L || game.getPlayer2Pid() == 23000000020606L) {
                    notificationServer.sendMoveNotification(playerData.getName(), game.getCurrentPlayer(), game.getGid(), GridStateFactory.getGameName(game.getGame()));
                }

                game.setUndoRequested(false);

                notificationServer.sendSilentNotification(game.getOpponent(game.getCurrentPlayer()));

                //redirect to somewhere
                String isMobile = (String) request.getParameter("mobile");
                if (isMobile == null) {
                    long gameId = 0;
                    String cycle = (String) request.getParameter("cycle");
                    if (cycle != null) {
                        long myPid = playerData.getPlayerID();
                        List<TBSet> sets = tbGameStorer.loadSets(myPid);
                        for (TBSet s : sets) {
                            if (s.getState() == TBGame.STATE_ACTIVE) {
                                if (s.getGame1().getState() == TBGame.STATE_ACTIVE &&
                                        (s.getGame1().getCurrentPlayer() == myPid ||
                                                (s.getCancelPid() != 0 && s.getCancelPid() != myPid))) {
                                    gameId = s.getGame1().getGid();
                                    break;
                                } else if (s.getGame2() != null &&
                                        s.getGame2().getState() == TBGame.STATE_ACTIVE &&
                                        (s.getGame2().getCurrentPlayer() == myPid ||
                                                (s.getCancelPid() != 0 && s.getCancelPid() != myPid))) {
                                    gameId = s.getGame2().getGid();
                                    break;
                                }
                            }
                        }
                    }

                    if (gameId != 0) {
                        String redirectPage = "/gameServer/tb/game?gid=" + gameId + "&command=load&mobile&cycle";
                        response.sendRedirect(redirectPage);
                    } else {
                        response.sendRedirect(moveRedirectPage);
                    }

                } else {
                    response.sendRedirect(mobileRedirectPage);
                }
            }

        } catch (TBStoreException tbe) {
            log4j.error("MoveServlet: " + gid, tbe);
            handleError(request, response, "Database error, please try again later.");
        } catch (Throwable t) {
            log4j.error("MoveServlet: " + gid, t);
            handleError(request, response, "Unknown error.");
        }

    }

    private void handleError(HttpServletRequest request,
                             HttpServletResponse response, String errorMessage) throws ServletException,
            IOException {
        request.setAttribute("error", errorMessage);
        getServletContext().getRequestDispatcher(errorRedirectPage).forward(
                request, response);
    }
}
