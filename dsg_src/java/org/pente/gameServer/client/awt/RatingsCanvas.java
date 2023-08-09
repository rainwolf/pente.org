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

public class RatingsCanvas extends Canvas {

    private Image ratingsImage;
    private Graphics ratingsGraphics;

    private static final Font headerFont =
            new Font("Arial", Font.BOLD, 14);
    private static final Font ratingsFont =
            new Font("Dialog", Font.PLAIN, 12);

    private static final Color GREEN_RATINGS_COLOR =
            new Color(11, 203, 11);
    private static final Color BLUE_RATINGS_COLOR =
            new Color(1, 85, 255);
    private static final Color YELLOW_RATINGS_COLOR =
            new Color(243, 235, 23);

    private Dimension minSize;
    private Dimension currentSize;

    private int maxTextWidth;
    private int headerHeight;
    private int rowHeight;
    private Color headerColor;

    public RatingsCanvas(Color headerColor) {
        minSize = new Dimension(0, 0);
        currentSize = new Dimension(0, 0);

        this.headerColor = headerColor;
    }


    public void addNotify() {
        super.addNotify();

        calculateFontMetrics();
        ratingsImage = createImage(minSize.width, minSize.height);
        ratingsGraphics = ratingsImage.getGraphics();
        ratingsGraphics.setClip(0, 0, minSize.width, minSize.height);
    }

    public void destroy() {
        if (ratingsGraphics != null) {
            ratingsGraphics.dispose();
            ratingsGraphics = null;
        }
        if (ratingsImage != null) {
            ratingsImage.flush();
            ratingsImage = null;
        }
    }

    public Dimension getPreferredSize() {
        return minSize;
    }

    public Dimension getMinimumSize() {
        return minSize;
    }

    private void sizeChanged(int x, int y) {

        calculateFontMetrics();

        if (ratingsImage != null &&
                x != 0 && y != 0) {

            // need a bigger image
            Rectangle rec = ratingsGraphics.getClipBounds();
            if (rec != null &&
                    (rec.width < x ||
                            rec.height < y)) {
                destroy();
                ratingsImage = createImage(x, y);
                ratingsGraphics = ratingsImage.getGraphics();
            }
            // use the same image but less of it
            else {
                ratingsGraphics.clearRect(0, 0, ratingsImage.getWidth(this), ratingsImage.getHeight(this));
            }

            // set the clip to the current size
            ratingsGraphics.clipRect(0, 0, x, y);
        }

        repaint();
    }

    private void calculateFontMetrics() {

        FontMetrics fontMetrics = getFontMetrics(headerFont);
        headerHeight = fontMetrics.getMaxAscent() +
                fontMetrics.getMaxDescent() +
                fontMetrics.getLeading();
        int width = fontMetrics.stringWidth("Ratings Key");


        fontMetrics = getFontMetrics(ratingsFont);
        rowHeight = fontMetrics.getMaxAscent() +
                fontMetrics.getLeading();

        int height = headerHeight + rowHeight * 6 + 5;
        minSize = new Dimension(width, height);

        maxTextWidth = fontMetrics.stringWidth("Pente Master");
    }

    public void update(Graphics g) {

        if (ratingsGraphics != null) {
            paint(g);
        }
    }

    public void paint(Graphics g) {

        if (ratingsGraphics != null) {
            try {
                Dimension size = getSize();
                if (size.width != currentSize.width ||
                        size.height != currentSize.height) {
                    sizeChanged(size.width, size.height);
                    currentSize = size;
                    drawRatings(ratingsGraphics);
                }

                g.drawImage(ratingsImage, 0, 0, this);

            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    private void drawRatings(Graphics g) {

        int width = getSize().width;
        int height = getSize().height;
        g.setColor(Color.white);
        g.fillRect(0, 0, width, height);

        g.setFont(headerFont);
        g.setColor(headerColor);
        g.drawString("Ratings Key", 5, headerHeight);
        g.setColor(Color.black);
        g.drawLine(0, headerHeight + 1, width - 1, headerHeight + 1);

        // shaded border
        g.setColor(new Color(128, 128, 128));
        g.drawLine(0, 0, 0, height - 2);
        g.drawLine(0, 0, width - 2, 0);

        g.setColor(Color.black);
        g.drawLine(1, 1, 1, height - 3);
        g.drawLine(1, 1, width - 3, 1);

        g.setColor(Color.white);
        g.drawLine(0, height - 1, width - 1, height - 1);
        g.drawLine(width - 1, height - 1, width - 1, 0);

        g.setColor(new Color(223, 223, 223));
        g.drawLine(1, height - 2, width - 2, height - 2);
        g.drawLine(width - 2, height - 2, width - 2, 1);
        // end shaded border

        int y = headerHeight + rowHeight + 1;

        g.setColor(Color.black);
        g.setFont(ratingsFont);
        g.drawString("1900+", 15, y);
        g.drawString("Pente Master", width - maxTextWidth - 10, y);
        g.setColor(Color.red);
        g.fillRect(6, y - 8, 7, 7);
        g.setColor(Color.black);
        g.drawRect(5, y - 9, 8, 8);

        y += rowHeight;
        g.setColor(Color.black);
        g.drawString("1700-1899", 15, y);
        g.drawString("Pente Expert", width - maxTextWidth - 10, y);
        g.setColor(YELLOW_RATINGS_COLOR);
        g.fillRect(6, y - 8, 7, 7);
        g.setColor(Color.black);
        g.drawRect(5, y - 9, 8, 8);

        y += rowHeight;
        g.setColor(Color.black);
        g.drawString("1400-1699", 15, y);
        g.drawString("Class A", width - maxTextWidth - 10, y);
        g.setColor(BLUE_RATINGS_COLOR);
        g.fillRect(6, y - 8, 7, 7);
        g.setColor(Color.black);
        g.drawRect(5, y - 9, 8, 8);

        y += rowHeight;
        g.setColor(Color.black);
        g.drawString("1000-1399", 15, y);
        g.drawString("Class B", width - maxTextWidth - 10, y);
        g.setColor(GREEN_RATINGS_COLOR);
        g.fillRect(6, y - 8, 7, 7);
        g.setColor(Color.black);
        g.drawRect(5, y - 9, 8, 8);

        y += rowHeight;
        g.setColor(Color.black);
        g.drawString("0-999", 15, y);
        g.drawString("Class C", width - maxTextWidth - 10, y);
        g.setColor(Color.gray);
        g.fillRect(6, y - 8, 7, 7);
        g.setColor(Color.black);
        g.drawRect(5, y - 9, 8, 8);

        y += rowHeight;
        g.setColor(Color.black);
        g.drawString("< 20 games", 15, y);
        g.drawString("Provisional", width - maxTextWidth - 10, y);

        g.setColor(Color.black);
        g.drawRect(5, y - 9, 8, 8);
    }
}