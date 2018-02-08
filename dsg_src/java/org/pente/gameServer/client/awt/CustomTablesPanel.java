package org.pente.gameServer.client.awt;

import java.awt.*;
import java.awt.event.*;
import java.util.*;

import org.pente.game.*;
import org.pente.gameServer.client.*;
import org.pente.gameServer.core.*;
import org.pente.gameServer.event.*;

public class CustomTablesPanel extends Panel
    implements MouseListener {

    private DSGPlayerData me;
    private Vector tables;

    private static final int X_OFF = 5;
    private static final int Y_OFF = 5;
    
    private static final int MIN_TABLES_DISPLAY = 3;
    private int tableHeight;
    private Dimension minSize;
    private Dimension currentSize;
    private static final Font bigFont = new Font("Dialog", Font.BOLD, 16);
    private static final Font smallFont = new Font("Dialog", Font.PLAIN, 10);
    private static final Font midFont = new Font("Dialog", Font.PLAIN, 12);
    private Image tablesImage;
    private Graphics tablesGraphics;
    private boolean scrollbarVisible = false;
    private Scrollbar scrollbar;

    private int startTable;
    private int visibleTables;

    private Vector joinListeners = new Vector();
    private GameStyles gameStyles;
    
    private final Object DRAW_LOCK = new Object();

    public CustomTablesPanel(DSGPlayerData me, GameStyles gameStyles) {

        this.me = me;
        this.gameStyles = gameStyles;

        // create scrollbar, add it when necessary later
        setLayout(new BorderLayout(0, 0));
        scrollbar = new Scrollbar(Scrollbar.VERTICAL);
        scrollbar.setUnitIncrement(1);
        scrollbar.setBlockIncrement(1);
        scrollbar.setMinimum(0);
        scrollbar.setValue(0);
        scrollbar.setVisibleAmount(1);

        // add listener to update startRow when scrollbar is adjusted
        scrollbar.addAdjustmentListener(new AdjustmentListener() {
            public void adjustmentValueChanged(AdjustmentEvent e) {
                // set start table to current value of scrollbar not events
                // value in case another thread updated scrollbar's value
                // between the time the user clicked and this event was
                // received
                startTable = scrollbar.getValue();
				if (startTable < 0) {
					startTable = 0;
				}
                repaint();
                requestFocus();
            }
        });

        tables = new Vector();
        
        minSize = new Dimension(0, 0);
        currentSize = new Dimension(0, 0);
        
        addMouseListener(this);
    }
    
    public void addTableJoinListener(TableJoinListener listener) {
        joinListeners.addElement(listener);
    }
    public void removeTableJoinListener(TableJoinListener listener) {
        joinListeners.removeElement(listener);
    }
    
    /** Determine the minimum size of this component */
    private void calculateFontMetrics() {

        FontMetrics fontMetrics = getFontMetrics(bigFont);

        tableHeight = (fontMetrics.getMaxAscent() +
            fontMetrics.getMaxDescent() +
            fontMetrics.getLeading() + Y_OFF) * 2;

        int height = MIN_TABLES_DISPLAY * tableHeight + 2 * Y_OFF;
        minSize = new Dimension(400, height);
    }

    /** once the components peer is created, now we can create the table
     *  image and graphics.
     */
    public void addNotify() {
        super.addNotify();

        calculateFontMetrics();
        tablesImage = createImage(minSize.width, minSize.height);
        tablesGraphics = tablesImage.getGraphics();
        tablesGraphics.setClip(0, 0, minSize.width, minSize.height);
    }
    
    /** release graphics and image from memory */
    public void destroy() {
        if (tablesGraphics != null) {
            tablesGraphics.dispose();
            tablesGraphics = null;
        }
        if (tablesImage != null) {
            tablesImage.flush();
            tablesImage = null;
        }
    }

    /** preferred size is static width and enough height for all tables */
    public Dimension getPreferredSize() {

        int numTables = tables.size();
        if (numTables < MIN_TABLES_DISPLAY) {
            numTables = MIN_TABLES_DISPLAY;
        }

        int height = numTables * tableHeight + 5;
        int width = minSize.width;
        if (scrollbarVisible) {
            width += scrollbar.getSize().width;
        }

        return new Dimension(width, height);
    }

    /** minimum size is static width and enough height for a few tables */
    public Dimension getMinimumSize() {

        int width = minSize.width;
        if (scrollbarVisible) {
            width += scrollbar.getSize().width;
        }
        return new Dimension(width, minSize.height);
    }

    /** overridden to make scrollbar not extend into border section */
    public Insets getInsets() {
        return new Insets(2, 0, 2, 2);
    }

    // mouse listener methods
    public void mouseClicked(MouseEvent e) {
        
        int table = getTableClicked(e);
        if (table == NO_TABLE_CLICKED) {
            return;
        }
        table = startTable + table;
        if (table >= tables.size()) {
            return;
        }
        CustomTableData data = (CustomTableData) tables.elementAt(table);
        for (int i = 0; i < joinListeners.size(); i++) {
            TableJoinListener l = (TableJoinListener) joinListeners.elementAt(i);
            l.joinTable(data.getTableNum());
        }
    }
    public void mouseEntered(MouseEvent e) {
    }
    public void mouseExited(MouseEvent e) {
    }
    public void mousePressed(MouseEvent e) {
        joinButtonClicked = getTableClicked(e);
        if (joinButtonClicked != NO_TABLE_CLICKED) {
            repaint();
        }
    }
    public void mouseReleased(MouseEvent e) {
        boolean p = false;
        if (joinButtonClicked != NO_TABLE_CLICKED) {
            p = true;
        }
        joinButtonClicked = NO_TABLE_CLICKED;
        if (p) {
            repaint();
        }
    }
    
    private static final int NO_TABLE_CLICKED = -1;
    /** Returns the VISIBLE table number that was clicked */
    private int getTableClicked(MouseEvent e) {
        
        if (joinButtonPosition == null || joinButtonSize == null) {
            return NO_TABLE_CLICKED;
        }
        for (int i = startTable, j = 0; i < getEndTable(); i++, j++) {

            int h = j * tableHeight + joinButtonPosition.height;
            int h2 = h + joinButtonSize.height;
            int w = joinButtonPosition.width;
            int w2 = w + joinButtonSize.width;
            
            if (e.getX() > w && e.getX() < w2 &&
                e.getY() > h && e.getY() < h2) {
                return j;
            }
        }
        
        return NO_TABLE_CLICKED;
    }
    
    // end mouse listener methods


    /** when the components size has changed, update number of visible tables,
     *  update the scrollbar, resize the image and graphics and add or remove
     *  the scrollbar
     */
    public void sizeChanged(Dimension newSize) {
        calculateFontMetrics();
        visibleTables = (newSize.height - 5) / tableHeight + 1;

        if (visibleTables <= 0) {
            visibleTables = MIN_TABLES_DISPLAY;
        }

        updateScrollbarMaximum();
        // need to reset startRow here in case visibleRows has
        // expanded or contracted
        updateStartRow();
        scrollbar.setValue(startTable);

        if (tablesImage != null &&
            newSize.width != 0 && newSize.height != 0) {

            // need a bigger image
            Rectangle rec = tablesGraphics.getClipBounds();
            if (rec != null &&
                (rec.width < newSize.width ||
                 rec.height < newSize.height)) {
                destroy();
                tablesImage = createImage(newSize.width, newSize.height);
                tablesGraphics = tablesImage.getGraphics();
            }
            // use the same image but less of it
            else {
                tablesGraphics.clearRect(0, 0, tablesImage.getWidth(this),
                    tablesImage.getHeight(this));
            }

            // set the clip to the current size
            tablesGraphics.clipRect(0, 0, newSize.width, newSize.height);
        }

        // add scrollbar if needed
        if (!scrollbarVisible && tables.size() >= visibleTables) {
            scrollbarVisible = true;
            add(scrollbar, "East");
        }
        // remove scrollbar if not needed
        else if (scrollbarVisible && tables.size() < visibleTables) {
            scrollbarVisible = false;
            remove(scrollbar);
        }

        // resizes scrollbar if necessary
        validate();
        repaint();
    }

    /** reduce flicker */
    public void update(Graphics g) {

        if (tablesGraphics != null) {
            paint(g);
        }
    }

    /** paint the whole component */
    public void paint(Graphics g) {

        if (tablesGraphics != null) {
            try {
                // check here if size has changed
                // i've had trouble just doing it in setSize()
                Dimension size = getSize();
                if (size.width != currentSize.width ||
                    size.height != currentSize.height) {
                    sizeChanged(size);
                    currentSize = size;
                }

                int width = getSize().width;
                int totalWidth = width;
                if (scrollbarVisible) {
                    width -= scrollbar.getSize().width;
                }

                int height = getSize().height;

                // clear screen
                tablesGraphics.setClip(0, 0, getSize().width, height);
                tablesGraphics.setColor(Color.white);
                tablesGraphics.fillRect(0, 0, getSize().width, height);

                // shaded border
                tablesGraphics.setColor(new Color(128, 128, 128));
                tablesGraphics.drawLine(0, 0, 0, height - 2);
                tablesGraphics.drawLine(0, 0, totalWidth - 2, 0);

                tablesGraphics.setColor(Color.black);
                tablesGraphics.drawLine(1, 1, 1, height - 3);
                tablesGraphics.drawLine(1, 1, totalWidth - 3, 1);

                tablesGraphics.setColor(Color.white);
                tablesGraphics.drawLine(0, height - 1, totalWidth - 1, height - 1);
                tablesGraphics.drawLine(totalWidth - 1, height - 1, totalWidth - 1, 0);

                tablesGraphics.setColor(new Color(223, 223, 223));
                tablesGraphics.drawLine(1, height - 2, totalWidth - 2, height - 2);
                tablesGraphics.drawLine(totalWidth - 2, height - 2, totalWidth - 2, 1);
                // end shaded border

                // draw each visible table
                synchronized (DRAW_LOCK) {
                    for (int i = startTable; i < getEndTable(); i++) {
                        CustomTableData data = (CustomTableData) tables.elementAt(i);
                        drawTable(tablesGraphics, data, i - startTable);
                    }
                }

                g.drawImage(tablesImage, 0, 0, this);

            } catch(Throwable t) {
                t.printStackTrace();
            }
        }
    }

    private Dimension joinButtonPosition;
    private Dimension joinButtonSize;
    private int joinButtonClicked = NO_TABLE_CLICKED;

    private void drawTable(Graphics g, CustomTableData data, int visibleTableNum) {
        
        int h = visibleTableNum * tableHeight;
        int width = getSize().width;
        if (scrollbarVisible) {
            width -= scrollbar.getSize().width;
        }

        FontMetrics fontMetrics = getFontMetrics(bigFont);

        int firstHeight = fontMetrics.getMaxAscent() +
            fontMetrics.getMaxDescent() +
            fontMetrics.getLeading();
        int firstWidth = fontMetrics.stringWidth("Table 10");
        int tableLabelWidth = fontMetrics.stringWidth("Table " + data.getTableNum());
        int joinWidth = fontMetrics.stringWidth("Join");
        
        fontMetrics = getFontMetrics(smallFont);
        int secondWidth1 = fontMetrics.stringWidth("Timed: ");
        int secondWidth2 = fontMetrics.stringWidth(" Keryo-Pente");
        int secondHeight = fontMetrics.getMaxAscent() +
            fontMetrics.getMaxDescent() +
            fontMetrics.getLeading();
        int privateWidth = fontMetrics.stringWidth("Private");
        
        fontMetrics = getFontMetrics(midFont);

        // 3 section borders offsets
        int s1 = firstWidth + X_OFF * 3;
        int s2 = s1 + secondWidth1 + secondWidth2 + X_OFF;
        int s3 = h + (tableHeight / 2 + Y_OFF / 2);
        int halfActualHeight = (tableHeight - Y_OFF) / 2;
        joinButtonPosition = new Dimension(X_OFF, Y_OFF + halfActualHeight + 2);
        joinButtonSize = new Dimension(s1 - X_OFF, halfActualHeight - 2);

        // set clip so watchers don't overflow
        g.setClip(X_OFF - 1, h + Y_OFF - 1, width - 2 * X_OFF + 1, tableHeight - Y_OFF + 1);
  
        // first section background
        g.setColor(gameStyles.boardBack);
        //if (data.isPublic() || (me != null && me.isAdmin())) {
            g.fillRect(X_OFF, h + Y_OFF, s1 - X_OFF, halfActualHeight + 2);
            g.setColor(gameStyles.buttonBack);
            g.fillRect(X_OFF, h + Y_OFF + halfActualHeight + 2, s1 - X_OFF, halfActualHeight - 2);

            // draw button background, shaded
            g.setColor(Color.white);
            int y = h + Y_OFF + halfActualHeight + 2;
            g.drawLine(X_OFF + 2, y, s1 - 2, y);
            g.drawLine(X_OFF + 2, y, X_OFF + 2, h + tableHeight - 3);
            
            g.setColor(new Color(241, 239, 226));
            g.drawLine(X_OFF + 3, y + 1, s1 - 3, y + 1);
            g.drawLine(X_OFF + 3, y + 1, X_OFF + 3, h + tableHeight - 4);
            
            g.setColor(new Color(113, 111, 100));
            g.drawLine(X_OFF + 2, h + tableHeight - 2, s1 - 1, h + tableHeight - 2);
            g.drawLine(s1 - 1, y, s1 - 1, h + tableHeight - 2);
            
            g.setColor(new Color(172, 168, 153));
            g.drawLine(X_OFF + 3, h + tableHeight - 3, s1 - 2, h + tableHeight - 3);
            g.drawLine(s1 - 2, y + 1, s1 - 2, h + tableHeight - 3);
        //}
        //else {
        //    g.fillRect(X_OFF, h + Y_OFF, s1 - X_OFF, tableHeight - Y_OFF);
        //}
        
        // 3rd section upper background
        g.setColor(new Color(255, 222, 165));
        g.fillRect(s2, h + Y_OFF, width - X_OFF - s2, halfActualHeight);
        
        // 3rd section bottom background
        g.setColor(gameStyles.buttonBack);
        g.fillRect(s2, h + Y_OFF + halfActualHeight, width - X_OFF - s2, halfActualHeight);

        // draw table border
        g.setColor(Color.black);
        g.drawRect(X_OFF, h + Y_OFF, width - 2 * X_OFF, tableHeight - Y_OFF);
        g.drawRect(X_OFF + 1, h + Y_OFF + 1, width - 2 * X_OFF - 2, tableHeight - Y_OFF - 2);

        // draw inner borders
        g.drawLine(s1, h + Y_OFF, s1, h + tableHeight);
        g.drawLine(s2, h + Y_OFF, s2, h + tableHeight);
        g.drawLine(s2, s3, width - X_OFF, s3);
        
        // draw first section - table number and join
        g.setFont(bigFont);
        g.setColor(gameStyles.foreGround);
        int tableLabelOffset = (s1 - tableLabelWidth - X_OFF) / 2 + X_OFF;
        g.drawString("Table " + data.getTableNum(), tableLabelOffset, h + Y_OFF + firstHeight);
        if (data.isPublic()) {
            if (visibleTableNum == joinButtonClicked) {
                g.setColor(Color.red);
            }
            else {
                g.setColor(Color.black);
            }
            int joinOffset = (s1 - joinWidth - X_OFF) / 2 + X_OFF;
            g.drawString("Join", joinOffset, h + Y_OFF + firstHeight * 2);
        }
        else {
            g.setColor(Color.black);
            g.setFont(smallFont);
            int privateOffset = (s1 - privateWidth - X_OFF) / 2 + X_OFF;
            int privateHeight = //me.isAdmin() ?
                //h + Y_OFF + (int) (firstHeight * 1.5) + (secondHeight / 2):
                h + Y_OFF + firstHeight + secondHeight;
            g.drawString("Private", privateOffset, privateHeight);
        }
        
        // draw second section - table state
        g.setFont(smallFont);
        g.setColor(Color.black);
        g.drawString("Game: ", s1 + X_OFF, h + Y_OFF + secondHeight);
        g.setColor(Color.red);
        Game normalGame = GridStateFactory.getGame(data.getGame());
        if (normalGame.isSpeed()) {
            normalGame = GridStateFactory.getNormalGame(normalGame);
        }
        g.drawString(normalGame.getName(),
            s1 + X_OFF + secondWidth1, h + Y_OFF + secondHeight);
        g.setColor(Color.black);
        g.drawString("Rated: ", s1 + X_OFF, h + Y_OFF + secondHeight * 2);
        g.drawString(data.isRated() ? "Yes" : "No",
            s1 + X_OFF + secondWidth1, h + Y_OFF + secondHeight * 2);
        String timedString = "No";
        if (data.isTimed()) {
            timedString = data.getInitialTime() + "/" + data.getIncrementalTime();
        }
        if (GridStateFactory.getGame(data.getGame()).isSpeed()) {
            timedString += " [Speed]";
        }
        g.drawString("Timed: ", s1 + X_OFF, h + Y_OFF + secondHeight * 3);
        g.drawString(timedString, s1 + X_OFF + secondWidth1, h + Y_OFF + secondHeight * 3);


        boolean go = normalGame.getId() == GridStateFactory.GO;
        // draw third section - players
        boolean bothPlayersSitting = true;
        g.setFont(midFont);
        if (data.getPlayerAtSeat(1) != null) {
            draw3DPiece(g, new Point(s2 + X_OFF, h + Y_OFF + 3),
                GameStyles.colors[(go?1:0)], 17);
            g.setColor(Color.black);
            g.drawString(data.getPlayerAtSeat(1), s2 + X_OFF + 20, s3 - Y_OFF);
        } else {
            bothPlayersSitting = false;
        }
        if (data.getPlayerAtSeat(2) != null) {
            draw3DPiece(g, new Point(width - 2 * X_OFF - 20, h + Y_OFF + 3),
                GameStyles.colors[(go?0:1)], 17);
            g.setColor(Color.black);
            int secondPlayerWidth = fontMetrics.stringWidth(data.getPlayerAtSeat(2));
            g.drawString(data.getPlayerAtSeat(2), width - 2 * X_OFF - 24 - secondPlayerWidth, s3 - Y_OFF);
        } else {
            bothPlayersSitting = false;
        }
        if (bothPlayersSitting) {
            int vsLocation = s2 + (width - X_OFF - s2 - fontMetrics.stringWidth("vs.")) / 2;
            g.drawString("vs.", vsLocation, s3 - Y_OFF);
        }
        
        g.setColor(Color.black);
        g.setFont(smallFont);
        String watching = "Watching: ";
        watching += "[" + data.getNumWatching() + "] ";
        for (Enumeration e = data.getWatchingPlayers(); e.hasMoreElements();) {
            DSGPlayerData d = (DSGPlayerData) e.nextElement();
            watching += d.getName() + " ";
        }
        g.drawString(watching, s2 + X_OFF, h + tableHeight - Y_OFF);
    }

    private static final Color shadowColor = new Color(60, 60, 60);

    void draw3DPiece(Graphics g, Point p, Color c[], int r) {

        int darkWidth = r - 1;
        int lightWidth = darkWidth * 5 / 6;
        int reflectionWidth = (darkWidth < 18) ? 4 : 6;
        int reflectionOffset = darkWidth / 4;

        fillOval(g, p.x + 2, p.y + 2, darkWidth, shadowColor); // shadow
        fillOval(g, p.x, p.y, darkWidth, c[0]);  // dark
        fillOval(g, p.x + 1, p.y + 1, lightWidth, c[1]); // light
        fillOval(g, p.x + reflectionOffset, p.y + reflectionOffset, reflectionWidth, Color.white); // reflection
    }

    void fillOval(Graphics g, int x, int y, int r, Color c) {
        g.setColor(c);
        g.fillOval(x, y, r, r);
    }


    // convenience methods
    private int getEndTable() {

        int endRow = startTable + visibleTables;
        int actualEndRow = tables.size();

        if (endRow > actualEndRow) {
            endRow = actualEndRow;
        }

        return endRow;
    }
    private void updateStartRow() {

        int endRow = getEndTable();
        if (endRow - startTable < visibleTables) {
            startTable = endRow - visibleTables;
        }
        if (startTable < 0) {
            startTable = 0;
        }
    }
    private void updateScrollbarMaximum() {

        int max = tables.size() - visibleTables + 2;
        if (max < 0) {
           max = 0;
        }
        scrollbar.setMaximum(max);
    }


    // API methods
    public void addTable(int tableNum) {
        CustomTableData d = new CustomTableData();
        d.setTableNum(tableNum);
		addTable(d);
    }
	
	private void addTable(CustomTableData d) {
        boolean addScrollbar = false;
		
        synchronized (DRAW_LOCK) {
            tables.addElement(d);

            updateScrollbarMaximum();

            // add scrollbar if needed
            if (!scrollbarVisible && tables.size() >= visibleTables) {
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

	public void removeTable(int tableNum) {
        
        boolean removeScrollbar = false;
        
        synchronized (DRAW_LOCK) {
            for (int i = 0; i < tables.size(); i++) {
                CustomTableData d = (CustomTableData) tables.elementAt(i);
                if (d.getTableNum() == tableNum) {
                    tables.removeElementAt(i);
                    break;
                }
            }

            updateStartRow();
            scrollbar.setValue(startTable);
            updateScrollbarMaximum();

            // add scrollbar if needed
            if (scrollbarVisible && tables.size() < visibleTables) {
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

    public void addPlayer(int tableNum, DSGPlayerData playerData) {
        
		if (playerData == null) {
            System.out.println("addPlayer is null!");
        }
        synchronized (DRAW_LOCK) {
            CustomTableData d = getTable(tableNum);
            if (d == null) {
                addTable(tableNum);
            }
            d = getTable(tableNum);
            d.addPlayer(playerData);
        }
        
        repaint();
    }

    public void removePlayer(int tableNum, String player) {

        synchronized (DRAW_LOCK) {
            standPlayer(tableNum, player);
            CustomTableData d = getTable(tableNum);
            if (d == null) {
                return;
            }
            d.removePlayer(player);
            if (d.isEmpty()) {
                removeTable(tableNum);
                return;
            }
        }
        
        repaint();
    }
    public void sitPlayer(int tableNum, String player, int seat) {

        synchronized(DRAW_LOCK) {
            CustomTableData d = getTable(tableNum);
            if (d == null) {
                return;
            }

            d.sitPlayer(player, seat);
        }
        
        repaint();
    }

    public void standPlayer(int tableNum, String player) {

        synchronized(DRAW_LOCK) {
            CustomTableData d = getTable(tableNum);
            if (d == null) {
                return;
            }
            d.standPlayer(player);
        }
        
        repaint();
    }

    public void swapPlayers(int tableNum) {
        synchronized(DRAW_LOCK) {
            CustomTableData d = getTable(tableNum);
            if (d == null) {
                return;
            }
            d.swapPlayers();
        }
        
        repaint();
    }

    public void changeTableState(int tableNum, DSGChangeStateTableEvent event) {
        synchronized (DRAW_LOCK) {
            CustomTableData d = getTable(tableNum);
			boolean newTable = false;
            if (d == null) {
				d = new CustomTableData();
				d.setTableNum(tableNum);
				newTable = true;
            }
            
            d.setGame(event.getGame());
            d.setIncrementalTime(event.getIncrementalSeconds());
            d.setInitialTime(event.getInitialMinutes());
            d.setRated(event.getRated());
            d.setTableType(event.getTableType());
            d.setTimed(event.getTimed());
			
			if (newTable) {
				addTable(d);
			}
        }

        repaint();
    }

    
    public CustomTableData getTable(int tableNum) {
        for (int i = 0; i < tables.size(); i++) {
            CustomTableData d = (CustomTableData) tables.elementAt(i);
            if (d.getTableNum() == tableNum) {
                return d;
            }
        }
        return null;
    }
}
