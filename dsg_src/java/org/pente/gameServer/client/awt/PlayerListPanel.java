/**
 * PlayerListPanel.java
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

package org.pente.gameServer.client.awt;

import java.awt.*;
import java.awt.event.*;
import java.util.*;

import org.pente.gameServer.client.*;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerGameData;

public class PlayerListPanel extends Panel
        implements PlayerListComponent {

    private Vector listeners = new Vector();

    private Vector players = new Vector();

    private String selectedPlayer;
    private String ownerPlayer = "";
    private String tableName;

    private Image playersImage;
    private Graphics playersGraphics;

    private Color selectedColor;

    private static final Font tableNameFont =
            new Font("Arial", Font.BOLD, 14);
    private static final Font playerFont =
            new Font("Dialog", Font.PLAIN, 12);
    private static final Font boldPlayerFont =
            new Font("Dialog", Font.BOLD, 12);
    private static final Font adminPlayerFont =
            new Font("Dialog", Font.BOLD | Font.ITALIC, 12);
    private static final Color GREEN_RATINGS_COLOR =
            new Color(11, 203, 11);
    private static final Color BLUE_RATINGS_COLOR =
            new Color(1, 85, 255);
    private static final Color YELLOW_RATINGS_COLOR =
            new Color(243, 235, 23);

    private Dimension minSize;
    private Dimension currentSize;
    private int minPlayers = 3;
    private int maxPlayersVisibleInitially = 6;
    private int headerHeight;
    private int rowHeight;
    private boolean scrollbarVisible = false;
    private Scrollbar scrollbar;
    private int startRow;
    private int visibleRows;
    private int oneCharWidth;

    private int ratingsColumnWidth;
    private static final int PLAYER_COLUMN = 1;
    private static final int RATINGS_COLUMN = 2;
    private int sortColumn = RATINGS_COLUMN;
    private static final int ASCENDING = 1;
    private static final int DESCENDING = -1;
    private int sortDir = DESCENDING;
    private int sortColumnPressed = 0;

    private int game = 1;
    private boolean showNumPlayers;

    public PlayerListPanel(PlayerListPanel toCopy) {
        this(toCopy.selectedColor);

        setTableName(toCopy.tableName);
        for (int i = 0; i < toCopy.players.size(); i++) {
            DSGPlayerData d = (DSGPlayerData) toCopy.players.elementAt(i);
            addPlayer(d);
        }

    }

    public PlayerListPanel(Color selectedColor) {

        this.selectedColor = selectedColor;

        // create scrollbar, add it when necessary later
        setLayout(new BorderLayout(0, 0));
        scrollbar = new Scrollbar(Scrollbar.VERTICAL);
        scrollbar.setUnitIncrement(1);
        scrollbar.setBlockIncrement(1);
        scrollbar.setMinimum(0);
        scrollbar.setValue(0);
        scrollbar.setVisibleAmount(1);

        // add listener to update startRow when scrollbar is adjusted
        scrollbar.addAdjustmentListener(e -> {
            // set start row to current value of scrollbar not events
            // value in case another thread updated scrollbar's value
            // between the time the user clicked and this event was
            // received
            startRow = scrollbar.getValue();
            repaint();
            requestFocus();
        });

        minSize = new Dimension(0, 0);
        currentSize = new Dimension(0, 0);

        addMouseListener(new MouseAdapter() {

            public void mousePressed(MouseEvent e) {
                int y = e.getY() - headerHeight - rowHeight;
                if (y < 0) {
                    selectedPlayer = null;

                    // pressed a header, just update button background
                    // action taken on release
                    if (y + rowHeight > 0) {
                        int x = e.getX();
                        if (x > getRealWidth() - ratingsColumnWidth) {
                            sortColumnPressed = RATINGS_COLUMN;
                        } else if (x > 0 && x < getRealWidth() - ratingsColumnWidth) {
                            sortColumnPressed = PLAYER_COLUMN;
                        }

                        repaint();
                    }
                } else {
                    int selectedNum = y / rowHeight + startRow;
                    synchronized (players) {
                        if (selectedNum < 0 || selectedNum > players.size() - 1) {
                            selectedPlayer = null;
                        } else {
                            selectedPlayer = ((DSGPlayerData)
                                    players.elementAt(selectedNum)).getName();
                        }
                    }
                    repaint();
                }
            }

            public void mouseReleased(MouseEvent e) {

                boolean p = false;
                int y = e.getY() - headerHeight - rowHeight;
                if (y < 0 && y + rowHeight > 0) { //released in a header
                    int x = e.getX();
                    if (sortColumnPressed == RATINGS_COLUMN &&
                            x > getRealWidth() - ratingsColumnWidth) {
                        p = true;
                        // change dir of ratings
                        if (sortColumn == RATINGS_COLUMN) {
                            if (sortDir == ASCENDING) {
                                sortDir = DESCENDING;
                            } else {
                                sortDir = ASCENDING;
                            }
                        }
                        // change from name to ratings
                        else {
                            sortColumn = RATINGS_COLUMN;
                            sortDir = DESCENDING;
                        }
                        sort();
                    } else if (sortColumnPressed == PLAYER_COLUMN &&
                            x > 0 && x < getRealWidth() - ratingsColumnWidth) {

                        p = true;
                        // change dir of names
                        if (sortColumn == PLAYER_COLUMN) {
                            if (sortDir == ASCENDING) {
                                sortDir = DESCENDING;
                            } else {
                                sortDir = ASCENDING;
                            }
                        }
                        // change from ratings to names
                        else {
                            sortColumn = PLAYER_COLUMN;
                            sortDir = ASCENDING;
                        }
                        sort();
                    }
                }

                // return button to normal state
                if (sortColumnPressed != 0) {
                    p = true;
                }
                sortColumnPressed = 0;

                if (p) {
                    repaint();
                }
            }

            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {

                    int y = e.getY() - headerHeight - rowHeight;
                    if (y < 0) {
                        selectedPlayer = null;
                    } else {
                        int selectedNum = y / rowHeight + startRow;

                        synchronized (players) {
                            if (selectedNum < 0 || selectedNum > players.size() - 1) {
                                return;
                            }
                            selectedPlayer = ((DSGPlayerData) players.elementAt(
                                    selectedNum)).getName();
                        }

                        for (int i = 0; i < listeners.size(); i++) {
                            PlayerActionListener l = (PlayerActionListener) listeners.elementAt(i);
                            l.actionRequested(selectedPlayer);
                        }
                    }
                }
            }
        });
    }

    public void setGame(int game) {

        // dont' think need to synch?
        if (this.game != game) {
            this.game = game;
            if (sortColumn == RATINGS_COLUMN) {
                sort();
            }
        }

        repaint();
    }

    public void addGetStatsListener(PlayerActionListener getStatsListener) {
        listeners.addElement(getStatsListener);
    }

    public void removeGetStatsListener(PlayerActionListener getStatsListener) {
        listeners.removeElement(getStatsListener);
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public void setOwner(String player) {

        synchronized (players) {

            this.ownerPlayer = player;

            // move owner to top of player list
            for (int i = 0; i < players.size(); i++) {

                DSGPlayerData d = (DSGPlayerData) players.elementAt(i);
                if (d.getName().equals(player)) {
                    players.removeElementAt(i);
                    players.insertElementAt(d, 0);
                    break;
                }
            }
        }

        repaint();
    }

    public void showNumPlayers(boolean show) {
        this.showNumPlayers = show;
    }

    public Enumeration getPlayers() {
        Vector playerNames = new Vector(players.size());
        for (int i = 0; i < players.size(); i++) {
            playerNames.addElement(((DSGPlayerData) players.elementAt(i)).getName());
        }
        return playerNames.elements();
    }

    public String getSelectedPlayer() {
        return selectedPlayer;
    }

    public void clearPlayers() {
        synchronized (players) {
            players.removeAllElements();
            selectedPlayer = null;
            startRow = 0;
            updateScrollbarMaximum();
            scrollbar.setValue(startRow);
            scrollbarVisible = false;
        }

        remove(scrollbar);
        validate();
        repaint();
    }

    private void sort() {
        synchronized (players) {
            Vector tmp = (Vector) players.clone();
            players.removeAllElements();
            for (int i = 0; i < tmp.size(); i++) {
                DSGPlayerData p = (DSGPlayerData) tmp.elementAt(i);
                insertPlayerSorted(p);
            }
        }
        repaint();
    }

    private void insertPlayerSorted(DSGPlayerData playerData) {
        int i = 0;
        for (; i < players.size(); i++) {

            DSGPlayerData existingPlayer = ((DSGPlayerData)
                    players.elementAt(i));
            // don't insert a name twice, sanity check
            if (existingPlayer.getName().equals(playerData.getName())) {
                return;
            }
            // insert owner at top
            else if (playerData.getName().equals(ownerPlayer)) {
                break;
            }
            // insert lower than owner
            else if (existingPlayer.getName().equals(ownerPlayer)) {
                continue;
            }
            // insert new player is admin
            else if (playerData.isAdmin()) {
                // insert higher than all non-admins
                if (!existingPlayer.isAdmin()) {
                    break;
                }
                // else insert in correct order within other admins
                else if (sortDir == comp(existingPlayer, playerData)) {
                    break;
                }
            } else {
                // insert new non-admin lower than admins
                if (existingPlayer.isAdmin()) {
                    continue;
                }
                // else insert in correct order within other players
                else if (sortDir == comp(existingPlayer, playerData)) {
                    break;
                }
            }
        }

        players.insertElementAt(playerData, i);
    }

    private int comp(DSGPlayerData existingPlayer, DSGPlayerData playerData) {
        int comp = 0;
        if (sortColumn == PLAYER_COLUMN) {
            comp = playerData.getName().compareTo(
                    existingPlayer.getName());
            if (comp > 0) comp = -1;
            else if (comp < 0) comp = 1;
        } else if (sortColumn == RATINGS_COLUMN) {
            DSGPlayerGameData p = playerData.getPlayerGameData(game);
            DSGPlayerGameData e = existingPlayer.getPlayerGameData(game);
            if (e.getTotalGames() == 0 && p.getTotalGames() > 0) comp = sortDir;
            else if (e.getTotalGames() > 0 && p.getTotalGames() == 0) comp = 0;
            else if (!e.isProvisional() && p.isProvisional()) comp = 0;
            else if (e.isProvisional() && !p.isProvisional()) comp = sortDir;
            else if (p.getRating() > e.getRating()) comp = -1;
            else comp = 1;
        } else comp = 0;

        return comp;
    }

    // player list order
    // 1. owner
    // 2. admins according to sort order
    // 3. other players according to sort order
    // * if sorting by rating, provisionals under established and
    // * 0-games under provisionals
    public void addPlayer(DSGPlayerData playerData) {

        if (playerData == null) {
            return;
        }

        boolean addScrollbar = false;

        synchronized (players) {

            insertPlayerSorted(playerData);
            updateScrollbarMaximum();

            // add scrollbar if needed
            if (!scrollbarVisible && players.size() > visibleRows) {
                scrollbarVisible = true;
                addScrollbar = true;
            }
        }

        // had to move the validate call outside synchronized block
        if (addScrollbar) {
            add(scrollbar, "East");
            validate();
        }

        repaint();
    }

    public void removePlayer(String playerName) {

        if (playerName == null) {
            return;
        }

        String playerLowerCase = playerName.toLowerCase();

        boolean removeScrollbar = false;

        synchronized (players) {

            for (int i = 0; i < players.size(); i++) {

                String existingPlayer = ((DSGPlayerData) players.elementAt(i)).getName();
                if (existingPlayer.equals(playerLowerCase)) {
                    players.removeElementAt(i);
                    if (existingPlayer.equals(selectedPlayer)) {
                        if (players.size() == 0) {
                            selectedPlayer = null;
                        } else if (i == players.size()) {
                            selectedPlayer = ((DSGPlayerData) players.elementAt(i - 1)).getName();
                        } else {
                            selectedPlayer = ((DSGPlayerData) players.elementAt(i)).getName();
                        }
                    }
                    break;
                }
            }

            updateStartRow();
            scrollbar.setValue(startRow);

            updateScrollbarMaximum();

            // remove scrollbar if not needed
            if (scrollbarVisible && players.size() <= visibleRows) {
                scrollbarVisible = false;
                removeScrollbar = true;
            }
        }

        if (removeScrollbar) {
            remove(scrollbar);
            validate();
        }

        repaint();
    }

    public void playerChanged(DSGPlayerData newData) {

        synchronized (players) {

            for (int i = 0; i < players.size(); i++) {
                String p = ((DSGPlayerData) players.elementAt(i)).getName();
                if (p.equals(newData.getName())) {
                    players.removeElementAt(i);
                    insertPlayerSorted(newData);
                    break;
                }
            }
        }

        repaint();
    }

    public void addNotify() {
        super.addNotify();

        calculateFontMetrics();
        playersImage = createImage(minSize.width, minSize.height);
        playersGraphics = playersImage.getGraphics();
        playersGraphics.setClip(0, 0, minSize.width, minSize.height);
    }

    public void destroy() {
        if (playersGraphics != null) {
            playersGraphics.dispose();
            playersGraphics = null;
        }
        if (playersImage != null) {
            playersImage.flush();
            playersImage = null;
        }
    }

    public Dimension getPreferredSize() {

        int rows = 0;
        // causing deadlock
        //synchronized (players) {
        rows = players.size();
        //}

        if (rows < minPlayers) {
            rows = minPlayers;
        }
        // added because if a bunch of players are in a table and a new player
        // enters the table, sometimes the player list starts out with that
        // size when really it should be smaller and have a scrollbar.  same
        // thing applies for the invite player dialog
        else if (rows > maxPlayersVisibleInitially) {
            rows = maxPlayersVisibleInitially;
        }

        int height = headerHeight + rows * rowHeight + 5;
        int width = minSize.width;
        if (scrollbarVisible) {
            width += scrollbar.getSize().width;
        }

        return new Dimension(width, height);
    }

    public Dimension getMinimumSize() {

        int width = minSize.width;
        if (scrollbarVisible) {
            width += scrollbar.getSize().width;
        }
        return new Dimension(width, minSize.height);
    }

    // overridden to make scrollbar not extend into table name section
    public Insets getInsets() {
        return new Insets(headerHeight + 2, 0, 2, 2);
    }

    private void sizeChanged(int x, int y) {

        calculateFontMetrics();
        visibleRows = (y - headerHeight - rowHeight - 5) / rowHeight;

        if (visibleRows <= 0) {
            visibleRows = minPlayers;
        }

        updateScrollbarMaximum();
        // need to reset startRow here in case visibleRows has
        // expanded or contracted
        updateStartRow();
        scrollbar.setValue(startRow);

        if (playersImage != null &&
                x != 0 && y != 0) {

            // need a bigger image
            Rectangle rec = playersGraphics.getClipBounds();
            if (rec != null &&
                    (rec.width < x ||
                            rec.height < y)) {
                destroy();
                playersImage = createImage(x, y);
                playersGraphics = playersImage.getGraphics();
            }
            // use the same image but less of it
            else {
                playersGraphics.clearRect(0, 0, playersImage.getWidth(this), playersImage.getHeight(this));
            }

            // set the clip to the current size
            playersGraphics.clipRect(0, 0, x, y);
        }

        // add scrollbar if needed
        if (!scrollbarVisible && players.size() > visibleRows) {
            scrollbarVisible = true;
            add(scrollbar, "East");
        }
        // remove scrollbar if not needed
        else if (scrollbarVisible && players.size() <= visibleRows) {
            scrollbarVisible = false;
            remove(scrollbar);
        }

        // resizes scrollbar if necessary
        validate();
        repaint();
    }

    private void calculateFontMetrics() {

        FontMetrics fontMetrics = getFontMetrics(tableNameFont);
        headerHeight = fontMetrics.getMaxAscent() +
                fontMetrics.getMaxDescent() +
                fontMetrics.getLeading();
        int width = fontMetrics.stringWidth("Main Room") + 20;


        fontMetrics = getFontMetrics(playerFont);
        rowHeight = fontMetrics.getMaxAscent() +
                fontMetrics.getMaxDescent() +
                fontMetrics.getLeading();
        oneCharWidth = fontMetrics.stringWidth("1");
        ratingsColumnWidth = fontMetrics.stringWidth("Rating") + 30;
        width += ratingsColumnWidth;

        int height = headerHeight + rowHeight * minPlayers + 5;
        minSize = new Dimension(width, height);
    }

    public void update(Graphics g) {

        if (playersGraphics != null) {
            paint(g);
        }
    }

    public void paint(Graphics g) {

        if (playersGraphics != null) {
            try {
                Dimension size = getSize();
                if (size.width != currentSize.width ||
                        size.height != currentSize.height) {
                    sizeChanged(size.width, size.height);
                    currentSize = size;
                }

                drawPlayers(playersGraphics);
                g.drawImage(playersImage, 0, 0, this);

            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    private int getRealWidth() {
        int width = getSize().width;
        if (scrollbarVisible) {
            width -= scrollbar.getSize().width;
        }
        return width;
    }

    private void drawPlayers(Graphics g) {

        int width = getSize().width;
        int totalWidth = width;
        if (scrollbarVisible) {
            width -= scrollbar.getSize().width;
        }

        int height = getSize().height;
        g.setColor(Color.white);
        g.fillRect(0, 0, totalWidth, height);

        if (tableName != null) {
            g.setFont(tableNameFont);
            g.setColor(selectedColor);
            g.drawString(tableName, 10, headerHeight);
            g.setColor(Color.black);
            g.drawLine(0, headerHeight + 1, totalWidth - 1, headerHeight + 1);
        }
        if (showNumPlayers) {

            g.setFont(tableNameFont);
            g.setColor(Color.red);
            g.drawString(Integer.toString(players.size()), totalWidth - 20, headerHeight);
        }

        // shaded border
        g.setColor(new Color(128, 128, 128));
        g.drawLine(0, 0, 0, height - 2);
        g.drawLine(0, 0, totalWidth - 2, 0);

        g.setColor(Color.black);
        g.drawLine(1, 1, 1, height - 3);
        g.drawLine(1, 1, totalWidth - 3, 1);

        g.setColor(Color.white);
        g.drawLine(0, height - 1, totalWidth - 1, height - 1);
        g.drawLine(totalWidth - 1, height - 1, totalWidth - 1, 0);

        g.setColor(new Color(223, 223, 223));
        g.drawLine(1, height - 2, totalWidth - 2, height - 2);
        g.drawLine(totalWidth - 2, height - 2, totalWidth - 2, 1);
        // end shaded border

        int y = headerHeight + 2;

        // draw headers
        g.setColor(new Color(188, 188, 188));
        g.fillRect(2, y, width - 4, rowHeight);

        // draw header buttons
        drawButton(2, y, width - ratingsColumnWidth, y + rowHeight, g,
                sortColumnPressed == PLAYER_COLUMN);
        drawButton(width - ratingsColumnWidth + 1, y, width - 2, y + rowHeight,
                g, sortColumnPressed == RATINGS_COLUMN);

        g.setColor(Color.black);
        g.setFont(playerFont);
        y += rowHeight;
        g.drawLine(2, y, width - 3, y);
        g.drawString("Name", 10, y - 3);
        g.drawString("Rating", width - ratingsColumnWidth + 5, y - 3);

        // draw sort indicator
        int sortX = 0;
        int sortY = y - 4;
        int pLen = 8;
        int sortY1, sortY2 = 0;
        if (sortColumn == PLAYER_COLUMN) {
            sortX = width - ratingsColumnWidth - pLen + 2;
        } else {
            sortX = width - pLen + 2;
        }

        if (sortDir == DESCENDING) {
            sortY1 = sortY - pLen;
            sortY2 = sortY;
        } else {
            sortY1 = sortY;
            sortY2 = sortY - pLen;
        }

        // fill
        Polygon p = new Polygon();
        // upper right point
        p.addPoint(sortX, sortY1);
        // upper right point
        p.addPoint(sortX - pLen, sortY1);
        // bottom point
        p.addPoint(sortX - pLen / 2, sortY2);

        g.setColor(Color.red);
        g.fillPolygon(p);

        // border
        p = new Polygon();
        // upper right point
        p.addPoint(sortX, sortY1);
        // upper left point
        p.addPoint(sortX - pLen, sortY1);
        // bottom point
        p.addPoint(sortX - pLen / 2, sortY2);

        g.setColor(Color.black);
        g.drawPolygon(p);


        // gridline down center
        g.setColor(new Color(230, 230, 230));
        g.drawLine(width - ratingsColumnWidth, headerHeight + 2,
                width - ratingsColumnWidth, height - 3);

        y += rowHeight;

        boolean anAdmin = false;
        boolean showingAdmins = true;
        synchronized (players) {
            int endRowTemp = getEndRow();
            int startRowTemp = startRow;

            for (int i = startRowTemp; i < endRowTemp; i++) {

                DSGPlayerData d = (DSGPlayerData) players.elementAt(i);
                // if (d == null) {
                //     continue;
                // }
                if (showingAdmins &&
                        (d.isAdmin() || ownerPlayer.equals(d.getName()))) {
                    anAdmin = true;
                } else if (showingAdmins && !d.isAdmin()) {
                    showingAdmins = false;
                    if (anAdmin) {
                        g.setColor(new Color(230, 230, 230));
                        g.drawLine(2, y - rowHeight + 1,
                                width - 3, y - rowHeight + 1);
                    }
                }
                DSGPlayerGameData gameData = d.getPlayerGameData(game);
                String player = d.getName();
                Color playerColor = d.getNameColor();

                if (d.hasPlayerDonated()) {
                    g.setFont(boldPlayerFont);
                    g.setColor(playerColor);
                } else {
                    g.setFont(playerFont);
                    g.setColor(Color.black);
                }
                if (d.isAdmin()) {
                    g.setFont(adminPlayerFont);
                }

                if (player.equals(selectedPlayer)) {

                    g.setColor(selectedColor);
                    g.fillRect(2, y - rowHeight + 1, width - ratingsColumnWidth - 3, rowHeight + 1);
                    g.setColor(Color.black);
                    g.drawRect(2, y - rowHeight + 1, width - ratingsColumnWidth - 3, rowHeight + 1);
                    g.setColor(Color.white);
                }

                if (player.equals(ownerPlayer)) {

                    Color currentColor = g.getColor();
                    g.setColor(Color.red);
                    g.fillRect(3, y - 6, 5, 5);
                    g.setColor(currentColor);
                }

                g.drawString(player, 10, y);

                if (d.getTourneyWinner() > DSGPlayerGameData.TOURNEY_WINNER_NONE) {
                    FontMetrics fontMetrics = getFontMetrics(playerFont);
                    drawCrown(g, fontMetrics.stringWidth(player) + 15, y,
                            d.getTourneyWinner());
                }

                if (gameData != null && gameData.getTotalGames() > 0) {
                    int r = (int) Math.round(gameData.getRating());
                    Color ratingsColor = null;
                    // determine color-code for rating
                    if (!gameData.isProvisional()) {
                        if (r >= 1900) {
                            ratingsColor = Color.red;
                        } else if (r >= 1700) {
                            ratingsColor = YELLOW_RATINGS_COLOR;
                        } else if (r >= 1400) {
                            ratingsColor = BLUE_RATINGS_COLOR;
                        } else if (r >= 1000) {
                            ratingsColor = GREEN_RATINGS_COLOR;
                        } else {
                            ratingsColor = Color.gray;
                        }
                    }

                    // draw colored box
                    if (ratingsColor != null) {
                        g.setColor(ratingsColor);
                        g.fillRect(width - ratingsColumnWidth + 5, y - 8, 7, 7);
                    }
                    g.setColor(Color.black);
                    g.drawRect(width - ratingsColumnWidth + 4, y - 9, 8, 8);

                    // draw rating
                    g.setFont(playerFont);
                    g.setColor(Color.black);
                    int ratingsX = width - ratingsColumnWidth + 15;
                    if (r < 1000) ratingsX += oneCharWidth;
                    g.drawString(Integer.toString(r), ratingsX, y);

                }

                y += rowHeight;
            }
        }
    }

    private static final Color CROWN_COLORS[] = new Color[]{
            null,
            new Color(255, 203, 79),
            new Color(192, 192, 192),
            new Color(180, 97, 0),
            new Color(255, 255, 255)};

    /** @param x - Where to start drawing to the right of
     *  @param y - Where to start drawing up from
     *  draws inside a 12x10 pixel space
     */
    private void drawCrown(Graphics g, int x, int y, int type) {

        int x1 = 3;
        int x2 = 6;
        int x3 = 9;
        int x4 = 12;

        Polygon p = new Polygon();
        p.addPoint(x, y);
        p.addPoint(x, y - 10);
        p.addPoint(x + x1, y - 7);
        p.addPoint(x + x2, y - 10);
        p.addPoint(x + x3, y - 7);
        p.addPoint(x + x4, y - 10);
        p.addPoint(x + x4, y);

        if (type <= DSGPlayerGameData.KINGOFTHEHILL_WINNER) {
            g.setColor(CROWN_COLORS[type]);
        } else {
            int rgb = (30 + 3 - type) * 255 / 30;
            g.setColor(new Color(rgb, rgb, rgb));
        }
        g.fillPolygon(p);

        g.setColor(Color.black);
        g.drawPolygon(p);

        // draw little dots on crown
        if (type > DSGPlayerGameData.KINGOFTHEHILL_WINNER + 7) {
            g.setColor(Color.white);
        }
        g.drawLine(x + x1, y - 4, x + x1, y - 4);
        g.drawLine(x + x2, y - 4, x + x2, y - 4);
        g.drawLine(x + x3, y - 4, x + x3, y - 4);
    }

    // convenience methods
    private int getEndRow() {

        int endRow = startRow + visibleRows;
        int actualEndRow = players.size();

        if (endRow > actualEndRow) {
            endRow = actualEndRow;
        }

        return endRow;
    }

    private void updateStartRow() {

        int endRow = getEndRow();
        if (endRow - startRow < visibleRows) {
            startRow = endRow - visibleRows;
        }
        if (startRow < 0) {
            startRow = 0;
        }
    }

    private void updateScrollbarMaximum() {

        int max = players.size() - visibleRows + 1;
        if (max < 0) {
            max = 0;
        }
        scrollbar.setMaximum(max);
    }

    private void drawButton(int x, int y, int x2, int y2, Graphics g, boolean pressed) {

        Color c1 = pressed ? new Color(113, 111, 100) : Color.white;
        Color c2 = pressed ? new Color(172, 168, 153) : new Color(241, 239, 226);
        Color c3 = pressed ? Color.white : new Color(113, 111, 100);
        Color c4 = pressed ? new Color(241, 239, 226) : new Color(172, 168, 153);
        g.setColor(c1);
        g.drawLine(x, y, x2 - 1, y);
        g.drawLine(x, y + 1, x, y2 - 1);

        g.setColor(c2);
        g.drawLine(x + 1, y + 1, x2 - 2, y + 1);
        g.drawLine(x + 1, y + 1, x + 1, y2 - 2);

        g.setColor(c3);
        g.drawLine(x, y2 - 1, x2 - 1, y2 - 1);
        g.drawLine(x2 - 1, y, x2 - 1, y2);

        g.setColor(c4);
        g.drawLine(x + 1, y2 - 2, x2 - 2, y2 - 2);
        g.drawLine(x2 - 2, y + 1, x2 - 2, y2 - 2);
    }
}