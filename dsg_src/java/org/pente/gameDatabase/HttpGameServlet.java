/** HttpGameServlet.java
 *  Copyright (C) 2001 Dweebo's Stone Games (http://www.pente.org/)
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, you can find it online at
 *  http://www.gnu.org/copyleft/gpl.txt
 */
package org.pente.gameDatabase;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.log4j.*;

import org.pente.database.*;
import org.pente.game.*;
import org.pente.filter.http.*;
import org.pente.filter.iyt.game.*;
import org.pente.gameServer.server.*;

public class HttpGameServlet extends HttpServlet {

    private static ObjectFormatFactory objectFormatFactory;
    private static HttpObjectFormat httpObjectFormat;

    private static final GameStorerSearchRequestData startRequestData = new SimpleHtmlGameStorerSearchRequestData();

    private static GameStorer gameStorer;
    private static GameStorerSearcher gameStorerSearcher;
    private static GameStats gameStats;

    private static ActivityLogger activityLogger;
    

    private static final int MAX_MOVES = 20;

    private static Category cat = Category.getInstance(HttpGameServlet.class.getName());

    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        try {

            ServletContext ctx = config.getServletContext();

			Resources resources = (Resources) ctx.getAttribute(Resources.class.getName());
			DBHandler dbHandler = resources.getDbHandlerRo();
            GameVenueStorer gameVenueStorer = (GameVenueStorer) ctx.getAttribute(GameVenueStorer.class.getName());

            // setup game stats objects
            gameStats = (GameStats) ctx.getAttribute(GameStats.class.getName());

            objectFormatFactory = new SimpleObjectFormatFactory("", "", "", "", gameStats);
            httpObjectFormat = new HttpObjectFormat(objectFormatFactory);

            // setup storers
            gameStorer = (GameStorer) ctx.getAttribute(GameStorer.class.getName());
            //playerStorer = (PlayerStorer) ctx.getAttribute(PlayerStorer.class.getName());
            gameStorerSearcher = (GameStorerSearcher) ctx.getAttribute(GameStorerSearcher.class.getName());

            // setup initial request data for html client 1st hit
            startRequestData.addMove(180);
            startRequestData.setGameStorerSearchResponseFormat("org.pente.gameDatabase.SimpleHtmlGameStorerSearchResponseFormat");
            startRequestData.setGameStorerSearchResponseOrder(GameStorerSearchResponseMoveDataComparator.SORT_GAMES);
            GameStorerSearchRequestFilterData filterData = new SimpleGameStorerSearchRequestFilterData();
            filterData.setGame(GridStateFactory.PENTE);
            filterData.setStartGameNum(0);
            filterData.setEndGameNum(100);
            startRequestData.setGameStorerSearchRequestFilterData(filterData);
            cat.info("init(), created startRequestData");

            activityLogger = (ActivityLogger) ctx.getAttribute(
                ActivityLogger.class.getName());
            
        } catch (Throwable t) {
            cat.error("Problem in init()", t);
        }
    }

    /*
    public void destroy() {

        cat.info("destroy(), destroying GameStats");
        if (gameStats != null) {
            gameStats.destroy();
        }
        cat.info("destroy(), destroying GameStorer");
        if (gameStorer != null) {
            gameStorer.destroy();
        }
        cat.info("destroy(), destroying PlayerStorer");
        if (playerStorer != null) {
            playerStorer.destroy();
        }
        cat.info("destroy(), destroying DBHandler");
        if (dbHandler != null) {
            dbHandler.destroy();
        }
    }
    */

    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
        throws ServletException, IOException {
        
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request,
                       HttpServletResponse response)
        throws ServletException, IOException {

        GameFormat gameFormat;
        String requestStr = null;
        boolean gzipOutput = false;

        try {

            gzipOutput = canGzipOutput(request);
            cat.debug("gzipOutput = " + gzipOutput);
            requestStr = request.getRequestURI();
            int lastSlash = requestStr.lastIndexOf('/');
            requestStr = requestStr.substring(lastSlash);

            cat.debug("requestStr = " + requestStr);


            if (requestStr.equals(HttpGameServer.LOAD_GAME)) {

                int status = HttpConstants.STATUS_SERVER_ERROR;
                String responseStr = "";

                String gameFormatClass = request.getParameter(HttpGameServer.GAME_FORMAT);

                gameFormat = (GameFormat) objectFormatFactory.createFormat(gameFormatClass);
                if (gameFormat != null) {

                    String gameIDStr = request.getParameter(HttpGameServer.GAME_ID);
                    if (gameIDStr != null) {
                        long gameID = Long.parseLong(gameIDStr);

                        if (gameStorer.gameAlreadyStored(gameID)) {

                            GameData gameData = gameStorer.loadGame(gameID, null);

                            if (gameData != null) {
                                StringBuffer buffer = new StringBuffer();
                                gameFormat.format(gameData, buffer);

                                status = HttpConstants.STATUS_OK;
                                responseStr = buffer.toString();
                            }
                            else {
                                status = HttpConstants.STATUS_NOT_FOUND;
                                responseStr = "Game not found: " + gameIDStr;
                            }
                        } else {
                            status = HttpConstants.STATUS_NOT_FOUND;
                            responseStr = "Game not found: " + gameIDStr;
                        }
                    }
                    else {
                        status = HttpConstants.STATUS_BAD_REQUEST;
                        responseStr = "Invalid request: Missing " + HttpGameServer.GAME_ID;
                    }
                }
                else {
                    status = HttpConstants.STATUS_BAD_REQUEST;
                    responseStr = "Invalid request: Missing, Invalid, or unsupported " + HttpGameServer.GAME_FORMAT + " - " +gameFormatClass;
                }

                sendResponse(response, status, responseStr, HttpConstants.CONTENT_TYPE_TEXT, false);
            }

            else if (requestStr.equals(HttpGameServer.SEARCH) ||
                requestStr.equals(HttpGameServer.SEARCH_ZIP)) {

                boolean downloadGames = requestStr.equals(HttpGameServer.SEARCH_ZIP);

                int status = HttpConstants.STATUS_SERVER_ERROR;
                String responseStr = "";
                String contentType = HttpConstants.CONTENT_TYPE_TEXT;

                GameStorerSearchRequestData requestData = null;
                if (request.getParameter(HttpGameServer.SEARCH_START_PARAM) != null) {
                    requestData = startRequestData;
                }
                else {

                    // build parameter buffer so that http object format
                    // cant decode it, yes its a little backwards but only because
                    // i switched to servlets...
                    StringBuffer paramsBuf = new StringBuffer();
                    Enumeration params = request.getParameterNames();
                    while (params.hasMoreElements()) {
                        String name = (String) params.nextElement();
                        String value = request.getParameter(name);

                        paramsBuf.append(name);
                        paramsBuf.append("=");
                        try {
                            paramsBuf.append(URLEncoder.encode(value, "UTF-8"));
                        } catch (UnsupportedEncodingException e) {
                        }
                        paramsBuf.append("&");
                    }

                    if (paramsBuf.length() > 1) {
                        paramsBuf.deleteCharAt(paramsBuf.length() - 1);
                    }

                    cat.debug("search params = " + paramsBuf.toString());
                    requestData = (GameStorerSearchRequestData)
                        httpObjectFormat.parse(null, paramsBuf);

                    // create this just to get hashcode of position for 
                    // activity logger
                    GridState hashState = GridStateFactory.createGridState(
                    	requestData.getGameStorerSearchRequestFilterData().getGame(), 
                    	requestData);

                    // if player banned from game db, return K10 position!
                    if (activityLogger.viewDb(
                        new ActivityData((String) request.getAttribute("name"),
                            request.getRemoteAddr()),
                        hashState.hashCode(),
                        requestData.getMoves(),
                        paramsBuf.toString())) {
                        
                        request.setAttribute("blocked", new Object());
                        requestData = startRequestData;
                    }
                    
                    // move this somewhere else!
                    int startGameNum = requestData.getGameStorerSearchRequestFilterData().getStartGameNum();
                    int endGameNum = requestData.getGameStorerSearchRequestFilterData().getEndGameNum();
                    int minIncrement = downloadGames ? 100 : 10;
                    int maxIncrement = 500;

                    if (startGameNum < 0) {
                        startGameNum = 0;
                    }
                    if (endGameNum <= startGameNum) {
                        endGameNum = startGameNum + minIncrement;
                    }
                    else if ((endGameNum - startGameNum) > maxIncrement) {
                        endGameNum = startGameNum + maxIncrement;
                    }
                    else if ((endGameNum - startGameNum) < minIncrement) {
                        endGameNum = startGameNum + minIncrement;
                    }
                    requestData.getGameStorerSearchRequestFilterData().setStartGameNum(startGameNum);
                    requestData.getGameStorerSearchRequestFilterData().setEndGameNum(endGameNum);
                }
                
                GameStorerSearchResponseData responseData2 = null;
                if (requestData != null) {
                    GameStorerSearchResponseFormat responseFormat = (GameStorerSearchResponseFormat) objectFormatFactory.createFormat(requestData.getGameStorerSearchResponseFormat());

                    int numGames = requestData.getGameStorerSearchRequestFilterData().getNumGames();
                    GameStorerSearchResponseData responseData1 = new SortedGameStorerSearchResponseData(GameStorerSearchResponseMoveDataComparator.SORT_GAMES, MAX_MOVES, numGames);
                    responseData2 = new SortedGameStorerSearchResponseData(requestData.getGameStorerSearchResponseOrder(), MAX_MOVES, numGames);

                    if (requestData.getNumMoves() > 0) {

                        gameStorerSearcher.search(requestData, responseData1);

                        // copy response data 1 to response data 2 if sorting is different
                        if (requestData.getGameStorerSearchResponseOrder() == GameStorerSearchResponseMoveDataComparator.SORT_GAMES) {
                            //if (requestData.getGameStorerSearchResponseOrder() == responseData1.getGameStorerSearchRequestData().getGameStorerSearchResponseOrder()) {
                             responseData2 = responseData1;
                        }
                        else {
                            responseData2.setGameStorerSearchRequestData(responseData1.getGameStorerSearchRequestData());

                            Vector resultMoves = responseData1.searchResponseMoveData();
                            for (int i = 0; i < resultMoves.size(); i++) {
                                GameStorerSearchResponseMoveData data = (GameStorerSearchResponseMoveData) resultMoves.elementAt(i);
                                responseData2.addSearchResponseMoveData(data);
                            }

                            responseData2.setRotation(responseData1.getRotation());
                            
                            Vector resultGames = responseData1.getGames();
                            for (int i = 0; i < resultGames.size(); i++) {
                                GameData data = (GameData) resultGames.elementAt(i);
                                responseData2.addGame(data);
                            }
                        }

                        if (downloadGames) {

                            ZipFileGameStorerSearchResponseStream zipStream = new ZipFileGameStorerSearchResponseStream();
                            gameFormat = (GameFormat) objectFormatFactory.createFormat("org.pente.game.PGNGameFormat");
                            zipStream.writeOutput(response, responseData2, gameFormat);
                            // don't call sendResponse below
                            return;
                        }
                        else {

                            if (responseFormat instanceof SimpleGameStorerSearchResponseFormat) {
								StringBuffer buffer = new StringBuffer();
	                            buffer = responseFormat.format(responseData2, buffer);
	
	                            status = HttpConstants.STATUS_OK;
	                            responseStr = buffer.toString();
	                            contentType = responseFormat.getContentType();
                            }
                        }
                    }
                    else {
                        status = HttpConstants.STATUS_BAD_REQUEST;
                        responseStr = "Invalid request: No moves specified";
                    }
                }
                else {
                    status = HttpConstants.STATUS_BAD_REQUEST;
                    responseStr = "Invalid request: parse exception";
                }

                if (responseData2 != null) {
                    request.setAttribute("responseData", responseData2);
                } else {
                    cat.debug("responseData is null");
                }
                request.setAttribute("gameStats", gameStats);

				if (responseStr != null && !responseStr.equals("")) {
					sendResponse(response, status, responseStr, contentType, false);
				}
				else {
					request.getRequestDispatcher("/gameServer/database.jsp").
    	                forward(request, response);
            	}
            }


        } catch (Throwable t) {

            cat.error("Unknown server error", t);

            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            t.printStackTrace(printWriter);

            int status = HttpConstants.STATUS_SERVER_ERROR;
            String responseStr = "Unknown server error, stack trace follows\n";
            responseStr += stringWriter.getBuffer().toString();

            sendResponse(response, status, responseStr, HttpConstants.CONTENT_TYPE_TEXT, false);
        }
    }

    /** Determine from the request whether or not the output can
     *  be sent gzipped.
     *  @param request The request, used to get headers
     *  @return boolean True if output can be gzipped
     */

    private boolean canGzipOutput(HttpServletRequest request) {

        boolean gzip = false;

        // look at all headers for "accept-encoding"
        Enumeration headers = request.getHeaderNames();
        while (headers.hasMoreElements()) {

            String headerName = (String) headers.nextElement();
            if (headerName.toLowerCase().equals("accept-encoding")) {

                String headerValue = request.getHeader(headerName);
                StringTokenizer encodingTokenizer = new StringTokenizer(headerValue, ",");
                while (encodingTokenizer.hasMoreTokens()) {

                    String encodingValue = encodingTokenizer.nextToken().trim();
                    String encoding = null;

                    int qIndex = encodingValue.indexOf(";");
                    if (qIndex != -1) {
                        if (encodingValue.charAt(qIndex + 2) != '0') {
                            encoding = encodingValue.substring(0, qIndex);
                        }
                    }
                    else {
                        encoding = encodingValue;
                    }

                    if (encoding.equals("gzip")) {
                        gzip = true;
                    }
                }
            }
        }

        return gzip;
    }

    private void sendResponse(HttpServletResponse response, int status,
                              String responseStr, String contentType,
                              boolean gzipOutput) throws IOException {

        PrintWriter out = null;
        if (gzipOutput) {
            out = new PrintWriter(new GZIPOutputStream(response.getOutputStream()));
            response.setHeader("Content-Encoding", "gzip");
        }
        else {
            out = response.getWriter();
        }

        response.setStatus(status);
        response.setContentType(contentType);
        response.setHeader("Cache-Control", "max-age=300");

        out.write(responseStr);
        out.close();
    }

    private GameData getGameData(String gameFormatClass) {

        if (gameFormatClass == null) {
            return null;
        }
        else if (gameFormatClass.equals("org.pente.filter.iyt.game.IYTPGNGameFormat")) {
            return new IYTGameData();
        }
        else if (gameFormatClass.equals("org.pente.game.PGNGameFormat")) {
            return new DefaultGameData();
        }
        else {
            return null;
        }
    }
}
