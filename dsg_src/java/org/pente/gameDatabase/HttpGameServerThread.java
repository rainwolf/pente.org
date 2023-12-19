/**
 * HttpGameServerThread.java
 * Copyright (C) 2001 Dweebo's Stone Games (http://www.pente.org/)
 * <p>
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, you can find it online at
 * http://www.gnu.org/copyleft/gpl.txt
 */

package org.pente.gameDatabase;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;
import java.util.zip.*;

import org.pente.game.*;
import org.pente.filter.http.*;
import org.pente.filter.iyt.game.*;

public class HttpGameServerThread implements Runnable {

    private static final ObjectFormatFactory objectFormatFactory = new SimpleObjectFormatFactory();
    private static final HttpObjectFormat httpObjectFormat = new HttpObjectFormat(objectFormatFactory);

    private static final int MAX_MOVES = 20;

    private Socket socket;
    private GameStorer gameStorer;
    private GameFormat gameFormat;
    private GameStorerSearcher gameStorerSearcher;

    // setup initial request data for html client 1st hit
    private static final GameStorerSearchRequestData startRequestData;

    static {
        startRequestData = new SimpleGameStorerSearchRequestData();
        startRequestData.addMove(180);
        startRequestData.setGameStorerSearchResponseFormat("org.pente.gameDatabase.SimpleHtmlGameStorerSearchResponseFormat");

        GameStorerSearchRequestFilterData filterData = new SimpleGameStorerSearchRequestFilterData();
        filterData.setStartGameNum(0);
        filterData.setEndGameNum(5);
        startRequestData.setGameStorerSearchRequestFilterData(filterData);
    }

    public HttpGameServerThread(Socket socket, GameStorer gameStorer, GameStorerSearcher gameStorerSearcher) {
        this.socket = socket;
        this.gameStorer = gameStorer;
        this.gameStorerSearcher = gameStorerSearcher;
    }

    public void run() {

        BufferedReader in = null;
        //BufferedWriter  out = null;
        //BufferedWriter  zipOut = null;
        OutputStream out = null;
        Hashtable<String, Object> params = null;
        String paramStr = null;

        boolean gzipOutput = false;
        int contentLength = 0;

        try {

            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = socket.getOutputStream();
            //out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            //zipOut = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(socket.getOutputStream())));

            params = new Hashtable<>();

            // the parsing of the http request is pretty much copied from iyt_server_thread,
            // maybe i should put this in another class?  is it worth it?
            String requestHeader = in.readLine();

            System.out.println(requestHeader);

            // get the method and request
            StringTokenizer requestTokenizer = new StringTokenizer(requestHeader, " ");
            String method = requestTokenizer.nextToken();
            String request = requestTokenizer.nextToken();

            // look for a query_string, if one exists call loadParams
            // to parse out the key/value pairs
            int queryStringIndex = request.indexOf("?");
            if (queryStringIndex >= 0) {
                paramStr = request.substring(queryStringIndex + 1);
                request = request.substring(0, queryStringIndex);

                HttpUtilities.parseParams(paramStr, params);
            }

            // read in http headers in content-length
            String header = null;
            while (true) {
                header = in.readLine();
                System.out.println(header);
                if (header == null) {
                    return;
                } else if (header.equals("")) {
                    break;
                } else {
                    int headerIndex = header.indexOf(":");
                    if (headerIndex >= 0) {

                        // get header key/value
                        String headerName = header.substring(0, headerIndex).trim();
                        String headerValue = header.substring(headerIndex + 1).trim();

                        if (headerName.toLowerCase().equals(HttpConstants.CONTENT_LENGTH.toLowerCase())) {
                            contentLength = Integer.parseInt(headerValue);
                        } else if (headerName.toLowerCase().equals("accept-encoding")) {
                            StringTokenizer encodingTokenizer = new StringTokenizer(headerValue, ",");
                            while (encodingTokenizer.hasMoreTokens()) {

                                String encodingValue = encodingTokenizer.nextToken().trim();
                                String encoding = null;

                                int qIndex = encodingValue.indexOf(";");
                                if (qIndex != -1) {
                                    if (encodingValue.charAt(qIndex + 2) != '0') {
                                        encoding = encodingValue.substring(0, qIndex);
                                    }
                                } else {
                                    encoding = encodingValue;
                                }

                                if (encoding.equals("gzip")) {
                                    gzipOutput = true;
                                }
                            }
                        }
                    }
                }
            }

//System.out.println("request="+request);
//System.out.println("gzip = " + gzipOutput);

            // if there is post data, parse the parameters
            if (contentLength > 0) {
                char postChars[] = new char[contentLength];
                in.read(postChars, 0, contentLength);
                paramStr = new String(postChars);
                System.out.println(paramStr);
                HttpUtilities.parseParams(paramStr, params);
            }

// ok, now we have the request and post data
//System.out.println(request);

            // need to move these locations to conf file
            File httpDocsDir = new File("httpdocs");
            File htmlDir = new File(httpDocsDir, "html");
            File jsDir = new File(httpDocsDir, "js");
            File imageDir = new File(httpDocsDir, "images");

            if (request.endsWith(".gif")) {
                File imageFile = new File(imageDir, request.substring(1));

                // ns4.7 can't handle gzipped images even though it sends the header
                // but ie5 and ns6.01 can...
                sendFile(out, imageFile, HttpConstants.CONTENT_TYPE_GIF, false);
            } else if (request.endsWith(".js")) {
                File jsFile = new File(jsDir, request.substring(1));

                // ns4.7 can't handle gzipped javascript files even though it sends the header
                // but ie5 and ns6.01 can...
                sendFile(out, jsFile, HttpConstants.CONTENT_TYPE_JS, false);
            } else if (request.endsWith(".html")) {
                File htmlFile = new File(htmlDir, request.substring(1));
                sendFile(out, htmlFile, HttpConstants.CONTENT_TYPE_HTML, gzipOutput);
            } else if (request.equals(HttpGameServer.GAME_ALREADY_STORED)) {

                int status = HttpConstants.STATUS_SERVER_ERROR;
                String response = "";
                boolean stored = false;

                String gameIDStr = (String) params.get(HttpGameServer.GAME_ID);
                if (gameIDStr != null) {
                    long gameID = Long.parseLong(gameIDStr);
                    stored = gameStorer.gameAlreadyStored(gameID);
                }

                if (stored) {
                    status = HttpConstants.STATUS_OK;
                    response = "Game found: " + gameIDStr;
                } else {
                    status = HttpConstants.STATUS_NOT_FOUND;
                    response = "Game not found: " + gameIDStr;
                }

                sendResponse(out, status, response, HttpConstants.CONTENT_TYPE_TEXT, gzipOutput);
            } else if (request.equals(HttpGameServer.LOAD_GAME)) {

                int status = HttpConstants.STATUS_SERVER_ERROR;
                String response = "";

                String gameFormatClass = (String) params.get(HttpGameServer.GAME_FORMAT);
                gameFormat = (GameFormat) objectFormatFactory.createFormat(gameFormatClass);
                if (gameFormat != null) {

                    String gameIDStr = (String) params.get(HttpGameServer.GAME_ID);
                    if (gameIDStr != null) {
                        long gameID = Long.parseLong(gameIDStr);

                        if (gameStorer.gameAlreadyStored(gameID)) {

                            GameData gameData = gameStorer.loadGame(gameID, null);

                            if (gameData != null) {
                                StringBuffer buffer = new StringBuffer();
                                gameFormat.format(gameData, buffer);

                                status = HttpConstants.STATUS_OK;
                                response = buffer.toString();
                            } else {
                                status = HttpConstants.STATUS_NOT_FOUND;
                                response = "Game not found: " + gameIDStr;
                            }
                        } else {
                            status = HttpConstants.STATUS_NOT_FOUND;
                            response = "Game not found: " + gameIDStr;
                        }
                    } else {
                        status = HttpConstants.STATUS_BAD_REQUEST;
                        response = "Invalid request: Missing " + HttpGameServer.GAME_ID;
                    }
                } else {
                    status = HttpConstants.STATUS_BAD_REQUEST;
                    response = "Invalid request: Missing, Invalid, or unsupported " + HttpGameServer.GAME_FORMAT + " - " + gameFormatClass;
                }

                sendResponse(out, status, response, HttpConstants.CONTENT_TYPE_TEXT, gzipOutput);
            } else if (request.equals(HttpGameServer.STORE_GAME)) {

                int status = HttpConstants.STATUS_SERVER_ERROR;
                String response = "";

                String gameFormatClass = (String) params.get(HttpGameServer.GAME_FORMAT);
                gameFormat = (GameFormat) objectFormatFactory.createFormat(gameFormatClass);
                if (gameFormat != null) {

                    String gameIDStr = (String) params.get(HttpGameServer.GAME_ID);
                    String game = (String) params.get(HttpGameServer.GAME_DATA);

                    if (gameIDStr != null && game != null) {

                        GameData gameData = getGameData(gameFormatClass);
                        gameFormat.parse(gameData, new StringBuffer(game));
                        gameData.setGameID(Long.parseLong(gameIDStr));

                        gameStorer.storeGame(gameData);

                        status = HttpConstants.STATUS_OK;
                        response = "Game stored successfully: " + gameIDStr;
                    } else {
                        status = HttpConstants.STATUS_BAD_REQUEST;
                        response = "Invalid request: Missing ";
                        response += (gameIDStr == null) ? HttpGameServer.GAME_ID : HttpGameServer.GAME_DATA;
                    }
                } else {
                    status = HttpConstants.STATUS_BAD_REQUEST;
                    response = "Invalid request: Missing, Invalid, or unsupported " + HttpGameServer.GAME_FORMAT + " - " + gameFormatClass;
                }

                sendResponse(out, status, response, HttpConstants.CONTENT_TYPE_TEXT, gzipOutput);
            } else if (request.equals(HttpGameServer.SEARCH)) {

                int status = HttpConstants.STATUS_SERVER_ERROR;
                String response = "";
                String contentType = HttpConstants.CONTENT_TYPE_TEXT;

                GameStorerSearchRequestData requestData = null;
                if (((String) params.get(HttpGameServer.SEARCH_START_PARAM)) != null) {
                    requestData = startRequestData;
                } else {
                    requestData = new SimpleGameStorerSearchRequestData();
                    requestData = (GameStorerSearchRequestData) httpObjectFormat.parse(requestData, new StringBuffer(paramStr));
                }

                if (requestData != null) {

                    GameStorerSearchResponseData responseData = new SortedGameStorerSearchResponseData(GameStorerSearchResponseMoveDataComparator.SORT_GAMES, MAX_MOVES, requestData.getGameStorerSearchRequestFilterData().getNumGames());

                    if (requestData.getNumMoves() > 0) {

                        gameStorerSearcher.search(requestData, responseData);

                        GameStorerSearchResponseFormat responseFormat = (GameStorerSearchResponseFormat) objectFormatFactory.createFormat(requestData.getGameStorerSearchResponseFormat());
                        StringBuffer buffer = new StringBuffer();
                        buffer = responseFormat.format(responseData, buffer);

                        status = HttpConstants.STATUS_OK;
                        response = buffer.toString();
                        contentType = responseFormat.getContentType();
                    } else {
                        status = HttpConstants.STATUS_BAD_REQUEST;
                        response = "Invalid request: No moves specified";
                    }
                } else {
                    status = HttpConstants.STATUS_BAD_REQUEST;
                    response = "Invalid request: parse exception";
                }

                sendResponse(out, status, response, contentType, gzipOutput);
            }

        } catch (Exception ex) {
            ex.printStackTrace();

            int status = HttpConstants.STATUS_SERVER_ERROR;
            String response = "Unknown server error";

            sendResponse(out, status, response, HttpConstants.CONTENT_TYPE_TEXT, gzipOutput);

        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                }
            }
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                }
            }
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private GameData getGameData(String gameFormatClass) {

        if (gameFormatClass == null) {
            return null;
        } else if (gameFormatClass.equals("org.pente.filter.iyt.game.IYTPGNGameFormat")) {
            return new IYTGameData();
        } else if (gameFormatClass.equals("org.pente.game.PGNGameFormat")) {
            return new DefaultGameData();
        } else {
            return null;
        }
    }

    private void sendResponse(OutputStream out, int status, String response, String contentType, boolean zip) {

        try {
            out.write(new String("HTTP/1.1 " + status + HttpConstants.END_LINE).getBytes());
            out.write(new String("Cache-Control: max-age=300" + HttpConstants.END_LINE).getBytes());
            //out.write(new String("Cache-Control: no-cache, no-store, private" + HttpConstants.END_LINE).getBytes());
            out.write(new String("Content-Type: " + contentType + HttpConstants.END_LINE).getBytes());

            if (zip) {
                out.write(new String("Content-Encoding: gzip" + HttpConstants.END_LINE).getBytes());
            }

            out.write(HttpConstants.END_LINE.getBytes());
            out.flush();

            if (zip) {
                out = new GZIPOutputStream(out);
            }

            out.write(response.getBytes());
            out.flush();

            // have to close here if we zip == true since out was changed and won't
            // be closed in calling method
            out.close();

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void sendFile(OutputStream out, File file, String contentType, boolean zip) {

        FileInputStream fileInputStream = null;

        try {

            int status = HttpConstants.STATUS_OK;

            out.write(new String("HTTP/1.1 " + status + HttpConstants.END_LINE).getBytes());
            out.write(new String("Content-type: " + contentType + HttpConstants.END_LINE).getBytes());

            if (zip) {
                out.write(new String("Content-Encoding: gzip" + HttpConstants.END_LINE).getBytes());
            } else {
                out.write(new String("Content-Length: ").getBytes());
                out.write(Long.valueOf(file.length()).toString().getBytes());
                out.write(HttpConstants.END_LINE.getBytes());
            }

            out.write(new String("Cache-Control: max-age=86400" + HttpConstants.END_LINE).getBytes());

            Calendar cal = Calendar.getInstance();
            TimeZone tz = TimeZone.getTimeZone("GMT");
            DateFormat dateFormat = new SimpleDateFormat("EEE, d MMM yyyy hh:mm:ss zzz");
            dateFormat.setTimeZone(tz);

            String currentDateStr = dateFormat.format(cal.getTime());
            cal.add(Calendar.DATE, 1);
            String expireDateStr = dateFormat.format(cal.getTime());
            Date lastModifiedDate = new Date(file.lastModified());
            String lastModifiedDateStr = dateFormat.format(lastModifiedDate);

            out.write(new String("Expires: " + expireDateStr + HttpConstants.END_LINE).getBytes());
            out.write(new String("Date: " + currentDateStr + HttpConstants.END_LINE).getBytes());
            out.write(new String("Last-Modified: " + lastModifiedDateStr + HttpConstants.END_LINE).getBytes());

            out.write(HttpConstants.END_LINE.getBytes());
            out.flush();

            if (zip) {
                out = new GZIPOutputStream(out);
            }

            fileInputStream = new FileInputStream(file);
            byte bytes[];

            while (true) {
                bytes = new byte[512];

                int count = fileInputStream.read(bytes);
                if (count == -1) {
                    break;
                }

                out.write(bytes);
                out.flush();
            }

            // have to close here if we zip == true since out was changed and won't
            // be closed in calling method
            out.close();

        } catch (IOException ex) {
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException ex) {
                }
            }
        }
    }
}