package org.pente.gameServer.client.web;

import java.io.*;

import java.awt.*;
import java.awt.image.*;
import javax.imageio.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.log4j.*;

import org.pente.game.*;
import org.pente.gameServer.core.*;
import org.pente.gameServer.client.PenteBoardComponent;
import org.pente.gameServer.client.awt.*;
import org.pente.gameServer.client.swing.PenteBoardLW;

public class BoardImageServlet extends HttpServlet {

    private static Category log4j =
        Category.getInstance(BoardImageServlet.class.getName());

    private GameStorer gameStorer;
    
    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        try {

            ServletContext ctx = config.getServletContext();

            gameStorer = (GameStorer) ctx.getAttribute(GameStorer.class.getName());

        } catch (Exception e) {
        	log4j.error("Error init()", e);
        }
    }


//    public long getLastModified(HttpServletRequest request) {
//        
//        DSGPlayerData dsgPlayerData = null;
//        String name = (String) request.getParameter("name");
//
//        try {
//            if (name == null) {
//                log4j.error("Player to view avatar last mod for is null");
//            }
//            else {
//                log4j.info("view player avatar last mod for " + name);
//                dsgPlayerData = dsgPlayerStorer.loadPlayer(name);
//            }
//
//        } catch (DSGPlayerStoreException e) {
//            log4j.info("view player avatar last mod for " + name + " failed", e);
//        }
//
//        if (dsgPlayerData == null || !dsgPlayerData.isActive() ||
//            !dsgPlayerData.hasAvatar()) {
//            log4j.error("Player not found or inactive or has no" +
//                "avatar, returning current time for last mod");
//            return System.currentTimeMillis();
//        }
//        else {
//            return dsgPlayerData.getAvatarLastModified();
//        }
//    }
//    
    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
        throws ServletException, IOException {
    	
    	String moves[] = null;
    	String game = null;
    	
    	int width = Integer.parseInt(request.getParameter("w"));
    	int height = Integer.parseInt(request.getParameter("h"));
    	

        PenteBoardLW lw = new PenteBoardLW();
        lw.gridCoordinatesChanged(new AlphaNumericGridCoordinates(19, 19));
    	GameBoard board = new GameBoard(null, lw);
    	
    	String gidStr = request.getParameter("gid");
    	if (gidStr != null && !gidStr.equals("")) {
    		long gid = Long.parseLong(gidStr);
    		try {
    			GameData gameData = gameStorer.loadGame(gid, new DefaultGameData());
            	board.setGameById(GridStateFactory.getGameId(
            		gameData.getGame()));

    	    	for (int move : gameData.getMoves()) {
    	    		board.getGridState().addMove(move);
    	    	}
    			
    		} catch (Exception e) {
            	log4j.error("Error init()", e);
    		}
    	}
    	else {
            game = request.getParameter("g");
            if (game == null || game.equals("")) {
                response.sendError(500, "Invalid game");
            }
            board.setGameById(GridStateFactory.getGameId(game));
            
            
    		String movesStr = request.getParameter("m");
    		if (movesStr != null && !movesStr.equals("")) {
                moves = movesStr.split(",");
    
    	    	for (String moveStr : moves) {
    	    		java.awt.Point p = board.getCoordinates().getPoint(moveStr);
    	    		int move = board.getGridState().convertMove(p.x, 18-p.y);
    	    		board.getGridState().addMove(move);
    	    	}
            }
            else {
                String bMovesStr = request.getParameter("bm");
                String wMovesStr = request.getParameter("wm");
                int t = 1;
                if (bMovesStr != null && !bMovesStr.equals("")) {
                    String bMoves[] = bMovesStr.split(",");
                    for (String bm : bMoves) {
                        java.awt.Point p = board.getCoordinates().getPoint(bm);
                        board.getGridBoard().addPiece(new SimpleGridPiece(p.x, p.y, 2), t++);
                    }
                }
                if (wMovesStr != null && !wMovesStr.equals("")) {
                    String wMoves[] = wMovesStr.split(",");
                    for (String wm : wMoves) {
                        java.awt.Point p = board.getCoordinates().getPoint(wm);
                        board.getGridBoard().addPiece(new SimpleGridPiece(p.x, p.y, 1), t++);
                    }
                }
                board.getGridBoard().visitLastTurn();
                board.getGridBoard().setHighlightPiece(null);
                
                String wCapsStr = request.getParameter("wc");
                String bCapsStr = request.getParameter("bc");
                if (wCapsStr != null && !wCapsStr.equals("")) {
                    for (int i = 0; i < Integer.parseInt(wCapsStr); i++) {
                        board.getGridBoard().removePiece(new SimpleGridPiece(1, 1, 1), t++);
                    }
                }
                if (bCapsStr != null && !bCapsStr.equals("")) {
                    for (int i = 0; i < Integer.parseInt(bCapsStr); i++) {
                        board.getGridBoard().removePiece(new SimpleGridPiece(1, 1, 2), t++);
                    }
                }
            }
    	}

    	PenteBoardLW c = (PenteBoardLW) board.getGridBoardComponent();
	    Image image = new BufferedImage(
	    	width, height, BufferedImage.TYPE_INT_RGB);
	    Graphics g = image.getGraphics();
	    c.setBoardInsets(0, 0, 0, 0);
	    c.myPaint(g, width, height);

        response.setContentType("image/png");
        response.setHeader("Cache-Control", "max-age=3600");
	    ImageIO.write((RenderedImage) image, "png", response.getOutputStream());
	    response.getOutputStream().flush();
    }
}
