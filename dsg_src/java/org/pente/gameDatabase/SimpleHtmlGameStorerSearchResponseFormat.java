/**
 * SimpleHtmlGameStorerSearchResponseFormat.java
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

import java.text.*;
import java.util.*;
import java.io.*;

import org.pente.game.*;
import org.pente.filter.http.*;

public class SimpleHtmlGameStorerSearchResponseFormat implements GameStorerSearchResponseFormat {

    private String indexUrl;
    private String basePath;
    private String imagePath;
    private String jsPath;
    private GameStats gameStats;

    private String BLANK_IMAGE;
    private String DOT_IMAGE;
    private String PLAYER_IMAGES[];

    private static final NumberFormat numberFormat = NumberFormat.getInstance();

    public SimpleHtmlGameStorerSearchResponseFormat() {
        this("/", "", "", "", null);
    }

    public SimpleHtmlGameStorerSearchResponseFormat(String indexUrl, String basePath, String jsPath, String imagePath, GameStats gameStats) {

        this.indexUrl = indexUrl;
        this.basePath = basePath;
        this.jsPath = jsPath;
        this.imagePath = imagePath;
        this.gameStats = gameStats;

        BLANK_IMAGE = imagePath + "blank.gif";
        DOT_IMAGE = imagePath + "dot.gif";
        PLAYER_IMAGES = new String[]{imagePath + "white.gif", imagePath + "black.gif"};
    }

    // not implemented
    public Object parse(Object obj, StringBuffer buffer) throws ParseException {
        return null;
    }

    public String getContentType() {
        return HttpConstants.CONTENT_TYPE_HTML;
    }

    public StringBuffer format(Object obj, StringBuffer buffer) {

        GameStorerSearchResponseData data;
        if (!(obj instanceof GameStorerSearchResponseData)) {
            throw new IllegalArgumentException("Object not GameStorerSearchResponseData");
        } else {
            data = (GameStorerSearchResponseData) obj;
        }

        buffer.append("<html>\r\n");
        buffer.append("<head><title>Game Database</title></head>\r\n");

        buffer.append("<body bgcolor=\"#FFDEA5\" link=\"#FFFFFF\" alink=\"#FFFFFF\" vlink=\"#FFFFFF\" ");
        buffer.append(formatBodyOnLoad(data));
        buffer.append(">\r\n");

        buffer.append("<table width=\"600\" border=\"0\" cellspacing=\"5\" cellpadding=\"5\">\r\n");

        if (gameStats != null) {
            formatGameStats(buffer);
        }

        buffer.append("<tr>\r\n");
        buffer.append("<td valign=\"top\">\r\n");
        formatBoard(data, buffer);

        try {
            buffer.append("<center>");
            formatForm(data, buffer);
            buffer.append("</center>");
        } catch (UnsupportedEncodingException e) {
        }

        buffer.append("</td>\r\n");

        buffer.append("<td valign=\"top\">\r\n");
        formatStatistics(data, buffer);
        buffer.append("</td></tr>\r\n");

        buffer.append("<tr><td colspan=\"2\">\r\n");
        formatFilterOptions(data, buffer);
        buffer.append("</td></tr>\r\n");

        try {
            buffer.append("<tr><td colspan=\"2\">\r\n");
            formatMatchedGames(data, buffer);
            buffer.append("</td></tr></table>\r\n");
        } catch (UnsupportedEncodingException e) {
        }

        buffer.append("</body>\r\n");
        buffer.append("</html>\r\n");

        return buffer;
    }

    private String formatBodyOnLoad(GameStorerSearchResponseData data) {

        String onLoad = "onload=\"javascript:initializeGame(); javascript:initSelects('filter_options_data', ";

        GameStorerSearchRequestFilterData filterData = data.getGameStorerSearchRequestData().getGameStorerSearchRequestFilterData();
        String site = filterData.getSite() == null ? "" : filterData.getSite();
        String event = filterData.getEvent() == null ? "" : filterData.getEvent();
        String round = filterData.getRound() == null ? "" : filterData.getRound();
        String section = filterData.getSection() == null ? "" : filterData.getSection();

        onLoad += "'" + safeSingleQuote(site) + "', ";
        onLoad += "'" + safeSingleQuote(event) + "', ";
        onLoad += "'" + safeSingleQuote(round) + "', ";
        onLoad += "'" + safeSingleQuote(section) + "');\"";

        return onLoad;
    }

    private String safeSingleQuote(String s) {

        StringBuffer sb = new StringBuffer(s);
        for (int i = 0; i < sb.length(); i++) {
            if (sb.charAt(i) == '\'') {
                sb.insert(i, '\\');
                i++;
            }
        }

        return sb.toString();
    }

    private void formatGameStats(StringBuffer buffer) {

        buffer.append("<tr><td colspan=\"2\">\r\n");
        buffer.append("<table width=\"100%\" border=\"1\" cellspacing=\"0\" cellpadding=\"1\" bordercolor=\"#000000\" bgcolor=\"#336633\">\r\n");
        buffer.append("<tr>\r\n");
        buffer.append("<td colspan=\"4\"><b><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\">");
        buffer.append("<a href=\"" + indexUrl + "\">Game Database</a></font></b></td>\r\n");
        buffer.append("</tr>\r\n");
        buffer.append("<tr>\r\n");
        buffer.append("<td><font face=\"Verdana, Arial, Helvetica, sans-serif\"><b><font color=\"#FFFFFF\"># Games</font></b></font></td>\r\n");
        buffer.append("<td><font face=\"Verdana, Arial, Helvetica, sans-serif\"><b><font color=\"#FFFFFF\"># Moves</font></b></font></td>\r\n");
        buffer.append("<td><font face=\"Verdana, Arial, Helvetica, sans-serif\"><b><font color=\"#FFFFFF\"># Players</font></b></font></td>\r\n");
        buffer.append("<td><font face=\"Verdana, Arial, Helvetica, sans-serif\"><b><font color=\"#FFFFFF\"># Sites</font></b></font></td>\r\n");
        buffer.append("</tr>\r\n");
        buffer.append("<tr>\r\n");
        buffer.append("<td><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\">");
        buffer.append(numberFormat.format((long) gameStats.getNumGames()));
        buffer.append("</font></td>\r\n");

        buffer.append("<td><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\">");
        buffer.append(numberFormat.format((long) gameStats.getNumMoves()));
        buffer.append("</font></td>\r\n");

        buffer.append("<td><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\">");
        buffer.append(numberFormat.format((long) gameStats.getNumPlayers()));
        buffer.append("</font></td>\r\n");

        buffer.append("<td><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\">");
        buffer.append(numberFormat.format((long) gameStats.getNumSites()));
        buffer.append("</font></td>\r\n");

        buffer.append("</tr>\r\n");
        buffer.append("</table>\r\n");
        buffer.append("<br>\r\n");
        buffer.append("</td></tr>\r\n");
    }

    private void formatAlphaCoordinates(StringBuffer buffer) {

        buffer.append("<td></td>");

        for (int i = 0; i < 19; i++) {

            char xx[] = new char[1];
            xx[0] = (char) (65 + i);
            if (xx[0] > 72) xx[0]++;
            String coord = new String(xx);

            buffer.append("<td>");
            buffer.append("<div align=\"center\"><font face=\"Verdana, Arial, Helvetica, sans-serif\">");
            buffer.append(coord);
            buffer.append("</font></div>");
            buffer.append("</td>\r\n");
        }

        buffer.append("<td></td>");
    }

    private String[][] buildBoardImages(GameStorerSearchResponseData data) {

        // fill the board with blank spaces
        String boardImages[][] = new String[19][19];
        for (int i = 0; i < boardImages.length; i++) {
            for (int j = 0; j < boardImages[i].length; j++) {
                boardImages[i][j] = BLANK_IMAGE;
            }
        }

        // put dots at tournament rule boundary
        boardImages[9][9] = DOT_IMAGE;
        boardImages[6][6] = DOT_IMAGE;
        boardImages[6][12] = DOT_IMAGE;
        boardImages[12][6] = DOT_IMAGE;
        boardImages[12][12] = DOT_IMAGE;

        // put query result moves on the board
        Vector searchResultsVector = data.searchResponseMoveData();
        for (int i = 0; i < searchResultsVector.size(); i++) {
            GameStorerSearchResponseMoveData moveData = (GameStorerSearchResponseMoveData) searchResultsVector.elementAt(i);
            int x = moveData.getMove() % 19;
            int y = moveData.getMove() / 19;
            boardImages[y][x] = imagePath + "light_green.gif";
        }

        return boardImages;
    }

    private void formatBoard(GameStorerSearchResponseData data, StringBuffer buffer) {

        buffer.append("<table cellspacing=\"0\" cellpadding=\"0\" border=\"0\">\r\n");
        buffer.append("<tr>\r\n");
        buffer.append("<td>&nbsp;</td>\r\n");
        formatAlphaCoordinates(buffer);
        buffer.append("<td>&nbsp;</td>\r\n");
        buffer.append("</tr>\r\n");

        // top row black border
        buffer.append("<tr><td></td>");
        buffer.append("<td colspan=\"21\" width=\"371\">");
        buffer.append("<img src=\"" + imagePath + "black_pixel.gif\" width=\"371\" height=\"5\"></td>");
        buffer.append("<td></td></tr>");

        String boardImages[][] = buildBoardImages(data);

        for (int i = 0; i < 19; i++) {

            buffer.append("<tr>\r\n");

            buffer.append("<td width=\"19\">");
            buffer.append("<div align=\"center\"><font face=\"Verdana, Arial, Helvetica, sans-serif\">");
            buffer.append((19 - i));
            buffer.append("</font></div>");
            buffer.append("</td>\r\n");

            // left column black border
            buffer.append("<td width=\"5\"><img src=\"" + imagePath + "black_pixel.gif\" width=\"5\" height=\"19\"></td>");

            for (int j = 0; j < 19; j++) {

                int move = i * 19 + j;
                String moveStr = PGNGameFormat.formatCoordinates(move);

                buffer.append("<td>");
                buffer.append("<a href=\"javascript:addMove('" + moveStr + "')\"");
                if (boardImages[i][j].equals(imagePath + "light_green.gif")) {
                    buffer.append(" onmouseover=\"javascript:highlightStat('" + moveStr + "s');\"");
                    buffer.append(" onmouseout=\"javascript:unHighlightStat('" + moveStr + "s');\"");
                }
                buffer.append(">");
                buffer.append("<img name=\"" + moveStr + "\" src=\"");
                buffer.append(boardImages[i][j]);
                buffer.append("\" width=\"19\" height=\"19\" border=\"0\"></a>");
                buffer.append("</td>\r\n");
            }

            // right column black border
            buffer.append("<td width=\"5\">");
            buffer.append("<img src=\"" + imagePath + "black_pixel.gif\" width=\"5\" height=\"19\"></td>");

            buffer.append("<td width=\"19\">");
            buffer.append("<div align=\"center\"><font face=\"Verdana, Arial, Helvetica, sans-serif\">");
            buffer.append((19 - i));
            buffer.append("</font></div>");
            buffer.append("</td>\r\n");

            buffer.append("</tr>\r\n");
        }

        // bottom row black border
        buffer.append("<tr><td></td>");
        buffer.append("<td colspan=\"21\" width=\"371\">");
        buffer.append("<img src=\"" + imagePath + "black_pixel.gif\" width=\"371\" height=\"5\"></td>");
        buffer.append("<td></td></tr>");

        buffer.append("<tr>\r\n");
        buffer.append("<td>&nbsp;</td>\r\n");
        formatAlphaCoordinates(buffer);
        buffer.append("<td>&nbsp;</td>\r\n");
        buffer.append("</tr>\r\n");

        String p[] = new String[]{"w", "b"};
        // for white and black capture rows
        for (int i = 0; i < 2; i++) {

            buffer.append("<tr><td>&nbsp;</td>\r\n");
            buffer.append("<td></td>\r\n");

            // for 5 pairs of captures
            for (int k = 0; k < 5; k++) {

                String imageNames[] = new String[]{p[i] + "c" + (k + 1) + "a",
                        p[i] + "c" + (k + 1) + "b",
                        "", ""};
                // for each capture pair put 2 images for stones and 2 blanks
                for (int l = 0; l < imageNames.length; l++) {

                    // only need 19, not 20
                    if (k == 4 && l == imageNames.length - 1) {
                        break;
                    }
                    buffer.append("<td><image src=\"" + imagePath + "stats_blank.gif\" ");
                    buffer.append("name=\"" + imageNames[l] + "\" ");
                    buffer.append("width=\"19\" height=\"19\"></td>\r\n");
                }
            }

            buffer.append("<td></td><td>&nbsp;</td>\r\n");
            buffer.append("</tr>\r\n");
        }

        buffer.append("</table>\r\n");
    }

    private void formatStatistics(GameStorerSearchResponseData data, StringBuffer buffer) {

        buffer.append("<div id=\"statsDiv\" style=\"position:relative\">\r\n");
        buffer.append("<table width=\"100\" border=\"1\" cellspacing=\"0\" cellpadding=\"1\" bordercolor=\"#000000\">\r\n");
        buffer.append("<tr bgcolor=\"#336633\">\r\n");
        buffer.append("<td colspan=\"4\"><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\"><b>Statistics</b></font></td>\r\n");
        buffer.append("</tr>\r\n");

        buffer.append("<tr bgcolor=\"#336633\">\r\n");
        String headers[] = new String[]{"#", "Move", "Games", "Wins"};
        int responseOrder = data.getGameStorerSearchRequestData().getGameStorerSearchResponseOrder() + 1;

        for (int i = 0; i < headers.length; i++) {

            String color = "#FFFFFF";
            String header = headers[i];

            // nothing special for #
            if (i > 0) {

                // highlight the current order in red
                if (responseOrder == i) {
                    color = "yellow";
                }
                // other orders have links
                else {
                    header = "<a href=\"javascript:sortResults('" + (i - 1) + "');\">" + header + "</a>";
                }
            }

            buffer.append("<td><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"").
                    append(color).
                    append("\" size=\"2\"><b>").
                    append(header).
                    append("</b></font></td>\r\n");
        }

        buffer.append("</tr>\r\n");

        int totalGames = 0;
        int totalWins = 0;

        Vector searchResults = data.searchResponseMoveData();
        for (int i = 0; i <= searchResults.size(); i++) {

            GameStorerSearchResponseMoveData moveData = null;

            // after all results shown, show totals
            if (i == searchResults.size()) {
                moveData = new SimpleGameStorerSearchResponseMoveData();
                moveData.setGames(totalGames);
                moveData.setWins(totalWins);
            } else {
                moveData = (GameStorerSearchResponseMoveData) searchResults.elementAt(i);

                totalGames += moveData.getGames();
                totalWins += moveData.getWins();
            }

            buffer.append("<tr bgcolor=\"#336633\">\r\n");

            if (i == searchResults.size()) {

                buffer.append("<td colspan=\"2\"><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\">");
                buffer.append("<b>Total</b>");
                buffer.append("</font></td>\r\n");
            } else {
                buffer.append("<td><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\">");
                buffer.append((i + 1));
                buffer.append("</font></td>\r\n");

                String move = PGNGameFormat.formatCoordinates(moveData.getMove());
                buffer.append("<td>");
                buffer.append("<img name=\"" + move + "s\" src=\"" + imagePath + "stats_blank.gif\">");
                buffer.append("<font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\">");
                buffer.append("<a href=\"javascript:addMove('" + move + "');\" ");
                buffer.append("onmouseover=\"javascript:highlightMove('" + move + "');\" ");
                buffer.append("onmouseout=\"javascript:unHighlightMove('" + move + "');\">");
                buffer.append(move);
                buffer.append("</a></font></td>\r\n");
            }

            buffer.append("<td><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\">");
            buffer.append(numberFormat.format((long) moveData.getGames()));
            buffer.append("</font></td>\r\n");

            NumberFormat percentFormat = NumberFormat.getPercentInstance();
            percentFormat.setMaximumFractionDigits(1);
            double rawp = moveData.getPercentage();
            String percent = percentFormat.format(rawp);

            buffer.append("<td><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\">");
            buffer.append(percent);
            buffer.append("</font></td>\r\n");
            buffer.append("</tr>\r\n");
        }

        buffer.append("</table>\r\n");
        buffer.append("</div>\r\n");
    }

    public void formatForm(
            GameStorerSearchResponseData data, StringBuffer buffer)
            throws UnsupportedEncodingException {

        SimpleGameStorerSearchRequestFormat requestFormat = new SimpleGameStorerSearchRequestFormat();
        SimpleGameStorerSearchResponseFormat responseFormat = new SimpleGameStorerSearchResponseFormat();
        StringBuffer moves = new StringBuffer();
        requestFormat.formatMoves(data.getGameStorerSearchRequestData(), moves, false, false);

        StringBuffer results = new StringBuffer();
        responseFormat.formatMoveResults(data, results, false);

        buffer.append("<form name=\"submit_form\" action=\"" + basePath + "/search\" method=\"post\">\r\n");
        buffer.append("<input type=\"hidden\" name=\"format_name\" value=\"org.pente.gameDatabase.SimpleGameStorerSearchRequestFormat\">\r\n");
        buffer.append("<input type=\"hidden\" name=\"format_data\" value=\"\">\r\n");
        buffer.append("</form>\r\n");

        buffer.append("<form name=\"data_form\">\r\n");
        buffer.append("<input type=\"hidden\" name=\"response_format\" value=\"org.pente.gameDatabase.SimpleHtmlGameStorerSearchResponseFormat\">\r\n");
        buffer.append("<input type=\"hidden\" name=\"moves\" value=\"" + moves.toString() + "\">\r\n");
        buffer.append("<input type=\"hidden\" name=\"results_order\" value=\"" + data.getGameStorerSearchRequestData().getGameStorerSearchResponseOrder() + "\">\r\n");

        int startNumDownloads = ((SimpleHtmlGameStorerSearchRequestData)
                data.getGameStorerSearchRequestData()).getStartZippedPartNum();
        buffer.append("<input type=\"hidden\" name=\"zippedPartNumParam\" value=\"" + startNumDownloads + "\">\r\n");

        buffer.append("<table width=\"100%\" border=\"0\">\r\n");
        buffer.append("<tr>\r\n");
        buffer.append("<td width=\"45%\" align=\"center\">\r\n");
        buffer.append("<input type=\"button\" onclick=\"javascript:firstMove()\" value=\" << \">\r\n");
        buffer.append("<input type=\"button\" onclick=\"javascript:backMove()\" value=\"  <  \">\r\n");
        buffer.append("<input type=\"button\" onclick=\"javascript:forwardMove()\" value=\"  >  \">\r\n");
        buffer.append("<input type=\"button\" onclick=\"javascript:lastMove()\" value=\" >> \">\r\n");
        buffer.append("</td>");
        buffer.append("<td width=\"55%\" align=\"center\">\r\n");
        buffer.append("<input type=\"button\" onclick=\"javascript:resetGame()\" value=\"Reset\">\r\n");
        buffer.append("<input type=\"button\" onclick=\"javascript:clearGame('K10')\" value=\"Clear\">\r\n");
        buffer.append("<input type=\"button\" onclick=\"javascript:search()\" value=\"Search\">\r\n");
        buffer.append("</td>\r\n");
        buffer.append("</tr>\r\n");
        buffer.append("</table>\r\n");

        buffer.append("</form>\r\n");

        buffer.append("<script language=\"javascript\">\r\n");
        buffer.append("var results = \"" + results.toString() + "\";\r\n");
        buffer.append("var numResults = \"" + data.getNumSearchResponseMoves() + "\";\r\n");
        buffer.append("var imagePath = \"" + imagePath + "\";\r\n");
        buffer.append("</script>\r\n");

        buffer.append("<script language=\"javascript\" src=\"" + jsPath + "database.js\"></script>\r\n");
    }

    public void formatMatchedGames(
            GameStorerSearchResponseData data, StringBuffer buffer)
            throws UnsupportedEncodingException {

        buffer.append("<form name=\"loadGameForm\" action=\"" + basePath + HttpGameServer.LOAD_GAME + "\" method=\"POST\">\r\n");
        buffer.append("<input type=\"hidden\" name=\"" + HttpGameServer.GAME_ID + "\" value=\"\">\r\n");
        buffer.append("<input type=\"hidden\" name=\"" + HttpGameServer.GAME_FORMAT + "\" value=\"org.pente.game.PGNGameFormat\">\r\n");
        buffer.append("</form>\r\n");

        buffer.append("<div id=\"gamesDiv\" style=\"position:relative\">\r\n");

        buffer.append("<form name=\"filter_data\">\r\n");
        GameStorerSearchRequestFilterData filterData = data.getGameStorerSearchRequestData().getGameStorerSearchRequestFilterData();
        buffer.append("<input type=\"hidden\" name=\"startGameNum\" value=\"" + filterData.getStartGameNum() + "\">\r\n");


        buffer.append("<table border=\"1\" cellpadding=\"1\" cellspacing=\"0\" bordercolor=\"#000000\" width=\"100%\">\r\n");
        buffer.append("<tr bgcolor=\"#0080C0\">\r\n");
        buffer.append("<td colspan=\"6\"><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\"><b>Recent Games</b></font></td>\r\n");
        buffer.append("<td colspan=\"3\" align=\"right\"><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\"><b>View</b>&nbsp;</font>\r\n");

        buffer.append("<select name=\"num_games\" onchange=\"javascript:changeNumMatchedGames();\">\r\n");

        // generate option list, make sure correct option is selected
        int numGames = filterData.getEndGameNum() - filterData.getStartGameNum();
        for (int i = 5; i < 30; i += 5) {

            buffer.append("<option value=\"");
            buffer.append(i);
            buffer.append("\"");
            if (i == numGames) {
                buffer.append(" selected");
            }
            buffer.append(">" + i + " games</option>\r\n");
        }

        buffer.append("</select>\r\n");
        buffer.append("</td></tr>\r\n");

        buffer.append("<tr bgcolor=\"#0080C0\">");
        buffer.append("<td><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\"><b>Txt</b></font></td>\r\n");
        buffer.append("<td><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\"><b>Load</b></font></td>\r\n");
        buffer.append("<td><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\"><b>Player 1</b></font></td>\r\n");
        buffer.append("<td><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\"><b>Player 2</b></font></td>\r\n");
        buffer.append("<td><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\"><b>Site</b></font></td>\r\n");
        buffer.append("<td><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\"><b>Event</b></font></td>\r\n");
        buffer.append("<td><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\"><b>Rnd</b></font></td>\r\n");
        buffer.append("<td><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\"><b>Sct</b></font></td>\r\n");
        buffer.append("<td><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\"><b>Date</b></font></td>\r\n");
        buffer.append("</tr>\r\n");

        Vector games = data.getGames();
        for (int i = 0; i < games.size(); i++) {
            GameData gameData = (GameData) games.elementAt(i);

            buffer.append("<tr bgcolor=\"#0080C0\">\r\n");

            buffer.append("<td><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\">");
            buffer.append("<a href=\"javascript:loadTxt('" + gameData.getGameID() + "');\">");
            buffer.append("Txt");
            buffer.append("</a>");
            buffer.append("</font></td>\r\n");

            SimpleGameStorerSearchRequestFormat searchFormat = new SimpleGameStorerSearchRequestFormat();
            StringBuffer movesBuf = new StringBuffer();
            movesBuf = searchFormat.formatMoves(gameData, movesBuf, false, false);
            buffer.append("<td><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\">");
            buffer.append("<a href=\"javascript:loadGame('" + movesBuf.toString() + "');\">");
            buffer.append("Load");
            buffer.append("</a>");
            buffer.append("</font></td>\r\n");

            String player1Color = "white";
            String player2Color = "white";
            if (gameData.getWinner() == GameData.PLAYER1) {
                player1Color = "yellow";
            } else if (gameData.getWinner() == GameData.PLAYER2) {
                player2Color = "yellow";
            }

            buffer.append("<td><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"");
            buffer.append(player1Color);
            buffer.append("\" size=\"2\">");
            buffer.append(gameData.getPlayer1Data().getUserIDName());
            buffer.append("</font></td>\r\n");

            buffer.append("<td><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"");
            buffer.append(player2Color);
            buffer.append("\" size=\"2\">");
            buffer.append(gameData.getPlayer2Data().getUserIDName());
            buffer.append("</font></td>\r\n");

            buffer.append("<td><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\">");
            buffer.append("<a href=\"" + gameData.getSiteURL() + "\">");
            buffer.append(gameData.getShortSite());
            buffer.append("</a>");
            buffer.append("</font></td>\r\n");

            buffer.append("<td><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\">");
            buffer.append(gameData.getEvent());
            buffer.append("</font></td>\r\n");

            String round = gameData.getRound();
            if (round == null) {
                round = "&nbsp;-";
            }
            buffer.append("<td><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\">");
            buffer.append(round);
            buffer.append("</font></td>\r\n");

            String section = gameData.getSection();
            if (section == null) {
                section = "&nbsp;-";
            }
            buffer.append("<td><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\">");
            buffer.append(section);
            buffer.append("</font></td>\r\n");

            SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
            String date = dateFormat.format(gameData.getDate());
            buffer.append("<td><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\">");
            buffer.append(date);
            buffer.append("</font></td>\r\n");

            buffer.append("</tr>\r\n");
        }

        buffer.append("<tr bgcolor=\"#0080C0\">\r\n");
        buffer.append("<td colspan=\"9\">\r\n");
        buffer.append("<table width=\"100%\">\r\n");
        buffer.append("<tr bgcolor=\"#0080C0\"><td width=\"25%\">\r\n");

        // if no previous games available, don't show link
        if (filterData.getStartGameNum() > 0) {
            buffer.append("<font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\">");
            buffer.append("<a href=\"javascript:prevGames();\">&lt;&lt; Prev Games</a></font>");
        } else {
            buffer.append("&nbsp;");
        }
        buffer.append("</td>\r\n");

        int actualEnd = filterData.getEndGameNum();
        boolean showNextLink = true;
        if (actualEnd > filterData.getTotalGameNum()) {
            actualEnd = filterData.getTotalGameNum();
            showNextLink = false;
        }
        String viewingCount = numberFormat.format((long) filterData.getStartGameNum() + 1)
                + "-" + numberFormat.format((long) actualEnd) + " of " +
                numberFormat.format((long) filterData.getTotalGameNum()) + " matched games";

        buffer.append("<td width=\"50%\" align=\"center\">\r\n");
        buffer.append("<font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\">");
        buffer.append(viewingCount);
        buffer.append("</font></td>\r\n");

        buffer.append("<td width=\"25%\" align=\"right\">\r\n");

        // if no more games available, don't show link
        if (showNextLink) {
            buffer.append("<font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\">");
            buffer.append("<a href=\"javascript:nextGames();\">Next Games &gt;&gt;</a></font>");
        } else {
            buffer.append("&nbsp;");
        }
        buffer.append("</td>\r\n");
        buffer.append("</tr></table>\r\n");
        buffer.append("</td></tr>\r\n");

        // put the links to zipped game files
        buffer.append("<tr bgcolor=\"#0080C0\">\r\n");
        buffer.append("<td colspan=\"9\">");
        buffer.append("<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" bordercolor=\"#000000\" width=\"100%\">\r\n");

        boolean showNextDlLink = true;
        boolean showPrevDlLink = false;
        int totalNumDownloads = ((filterData.getTotalGameNum() - 1) / 100) + 1;
        if (filterData.getTotalGameNum() == 0) {
            totalNumDownloads = 0;
        }

        int startNumDownloads = ((SimpleHtmlGameStorerSearchRequestData)
                data.getGameStorerSearchRequestData()).getStartZippedPartNum();
        if (startNumDownloads > totalNumDownloads) {
            startNumDownloads = 1;
        }
        if (startNumDownloads != 1) {
            showPrevDlLink = true;
        }
        int endNumDownloads = startNumDownloads + 10;
        if (endNumDownloads > totalNumDownloads) {
            endNumDownloads = totalNumDownloads + 1;
            showNextDlLink = false;
        }
        int shownDownloads = endNumDownloads - startNumDownloads;
        int prevStartNumDownloads = startNumDownloads - 10;
        if (prevStartNumDownloads < 1) {
            prevStartNumDownloads = 1;
        }


        buffer.append("<tr>\r\n");
        buffer.append("<td colspan=\"13\">");
        buffer.append("<font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\">");
        buffer.append("<b>Download Zipped Games (Grouped in 100's)</b></font></td>\r\n");
        buffer.append("</tr>");

        buffer.append("<tr>");
        buffer.append("<td width=\"16%\"><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\">");
        buffer.append("Part");
        buffer.append("</font></td>");

        buffer.append("<td width=\"7%\"><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\">");
        String prevLink = (showPrevDlLink) ? "<a href=\"javascript:changeDownloads('" + prevStartNumDownloads + "');\">&lt;&lt;</a>" : "&nbsp;";
        buffer.append(prevLink);
        buffer.append("</font></td>");

        for (int i = startNumDownloads; i < endNumDownloads; i++) {
            buffer.append("<td width=\"7%\"><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\">");
            buffer.append("<a href=\"javascript:downloadGames('" + i + "');\">" + i + "</a></font></td>");
        }

        buffer.append("<td width=\"7%\"><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\">");
        String nextLink = (showNextDlLink) ? "<a href=\"javascript:changeDownloads('" + endNumDownloads + "');\">&gt;&gt;</a>" : "&nbsp;";
        buffer.append(nextLink);
        buffer.append("</font></td>");

        if (shownDownloads < 10) {
            int width = (10 - shownDownloads) * 7;
            buffer.append("<td colspan=\"" + (10 - shownDownloads) + "\" width=\"" + width + "%\">&nbsp;</td>");
        }

        buffer.append("</tr>");

        buffer.append("</table></td></tr>");

        buffer.append("</table>\r\n");
        buffer.append("</form>\r\n");
        buffer.append("</div>\r\n");


    }

    public void formatFilterOptions(GameStorerSearchResponseData responseData, StringBuffer buffer) {
        GameStorerSearchRequestFilterData filterData = responseData.getGameStorerSearchRequestData().getGameStorerSearchRequestFilterData();
        String player1Name = filterData.getPlayer1Name();
        if (player1Name == null) {
            player1Name = "";
        }
        String player2Name = filterData.getPlayer2Name();
        if (player2Name == null) {
            player2Name = "";
        }

        String afterDate = "";
        if (filterData.getAfterDate() != null) {
            // subtract 1 day since javascript adds one day
            Calendar cal = Calendar.getInstance();
            cal.setTime(filterData.getAfterDate());
            cal.add(Calendar.DATE, -1);
            afterDate = SimpleGameStorerSearchRequestFilterFormat.shortDateFormat.format(cal.getTime());
        }
        String beforeDate = "";
        if (filterData.getBeforeDate() != null) {
            beforeDate = SimpleGameStorerSearchRequestFilterFormat.shortDateFormat.format(filterData.getBeforeDate());
        }

        buffer.append("<script language=\"javascript\" src=\"" + jsPath + "sites.js\"></script>\r\n");
        // using indexURL here because ppl at ebizhosting wouldn't configure apache
        // to send jsPath + "sitesData.js" to tomcat, they will send /*.js though
        buffer.append("<script language=\"javascript\" src=\"" + jsPath + "sitesData.js\"></script>\r\n");

        buffer.append("<form name=\"filter_options_data\">\r\n");

        String selectNames[] = new String[]{"Site", "Event", "Round", "Section"};
        StringBuffer table[][] = new StringBuffer[5][4];
        for (int i = 0; i < table.length; i++) {
            for (int j = 0; j < table[i].length; j++) {
                table[i][j] = new StringBuffer();
            }
        }

        for (int i = 0; i < selectNames.length; i++) {

            table[i][0].append("<font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\"><b>");
            table[i][0].append(selectNames[i]);
            table[i][0].append("</b></font>");
            table[i][1].append("<select name=\"");
            table[i][1].append(selectNames[i].toLowerCase() + "Select");
            table[i][1].append("\" onchange=\"javascript:");
            table[i][1].append(selectNames[i].toLowerCase() + "SelectChange();\"");
            table[i][1].append(" tabindex=\"" + (i + 1) + "\">\r\n");
            table[i][1].append("<option>");
            for (int j = 0; j < 30; j++) {
                table[i][1].append("&nbsp;");
            }
            table[i][1].append("</option>\r\n");
            for (int j = 0; j < 4; j++) {
                table[i][1].append("<option>&nbsp;</option>\r\n");
            }
            table[i][1].append("</select>");
        }

        table[0][2].append("<font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\"><b>Player 1 Name</b></font></td>");
        table[0][3].append("<input type=\"text\" name=\"" + SimpleGameStorerSearchRequestFilterFormat.PLAYER_1_NAME_PARAM + "\" value=\"" + player1Name + "\" size=\"10\" tabindex=\"5\">");

        table[1][2].append("<font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\"><b>Player 2 Name</b></font>");
        table[1][3].append("<input type=\"text\" name=\"" + SimpleGameStorerSearchRequestFilterFormat.PLAYER_2_NAME_PARAM + "\" value=\"" + player2Name + "\" size=\"10\" tabindex=\"6\">");

        table[2][2].append("<font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\"><b>After Date</b></font>");
        table[2][3].append("<input type=\"text\" name=\"" + SimpleGameStorerSearchRequestFilterFormat.AFTER_DATE_PARAM + "\" value=\"" + afterDate + "\" size=\"10\" maxlength=\"10\" tabindex=\"7\">");

        table[3][2].append("<font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\"><b>Before Date</b></font>");
        table[3][3].append("<input type=\"text\" name=\"" + SimpleGameStorerSearchRequestFilterFormat.BEFORE_DATE_PARAM + "\" value=\"" + beforeDate + "\" size=\"10\" maxlength=\"10\" tabindex=\"8\">");

        table[4][0].append("&nbsp;");
        table[4][1].append("&nbsp;");
        table[4][2].append("<font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\"><b>Winner</b></font>");
        table[4][3].append("<select name=\"selectWinner\" tabindex=\"9\">");

        String winnerSelectNames[] = new String[]{"Either player", "Player 1", "Player 2"};
        for (int i = 0; i < winnerSelectNames.length; i++) {
            table[4][3].append("<option value=\"" + i + "\"");
            if (filterData.getWinner() == i) {
                table[4][3].append(" selected");
            }
            table[4][3].append(">" + winnerSelectNames[i] + "</option>");
        }
        table[4][3].append("</select>");

        // print the table
        buffer.append("<table align=\"left\" border=\"1\" cellspacing=\"0\" cellpadding=\"2\" bordercolor=\"#000000\" width=\"100%\">\r\n");
        buffer.append("<tr bgcolor=\"#800080\">\r\n");
        buffer.append("<td colspan=\"" + table.length + "\"><font face=\"Verdana, Arial, Helvetica, sans-serif\" color=\"#FFFFFF\" size=\"2\"><b>Filter options</b></font></td>\r\n");
        buffer.append("</tr>\r\n");
        for (int i = 0; i < table.length; i++) {

            buffer.append("<tr bgcolor=\"#800080\">\r\n");
            for (int j = 0; j < table[i].length; j++) {
                buffer.append("<td>");
                buffer.append(table[i][j]);
                buffer.append("</td>\r\n");
            }
            buffer.append("</tr>\r\n");
        }
        buffer.append("</table>\r\n");
        buffer.append("</form>\r\n");
    }
}