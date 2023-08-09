/**
 * InsetPanel.java
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

/**
 * Simple Panel wrapper that provides insets.
 */
public class InsetPanel extends Panel {
    private int left = 0;
    private int right = 0;
    private int top = 0;
    private int bottom = 0;

    private boolean drawBorder;
    private int borderSize;

    /**
     * Constructor.
     * The 4 insets you want set for the Panel, doesn't draw a border.
     */
    public InsetPanel(int left, int right, int top, int bottom) {
        this.left = left;
        this.right = right;
        this.top = top;
        this.bottom = bottom;
        this.drawBorder = false;
        this.borderSize = 0;
    }

    /**
     * Constructor.
     * The 4 insets you want set for the panel, plus the border size.
     */
    public InsetPanel(int left, int right, int top, int bottom, int borderSize) {
        this.left = left;
        this.right = right;
        this.top = top;
        this.bottom = bottom;
        this.drawBorder = true;
        this.borderSize = borderSize;
    }


    /**
     * This is called by layout manager to set appropriate insets for panel.
     * @returns new Insets
     */
    public Insets getInsets() {
        return new Insets(top, left, bottom, right);
    }

    public void paint(Graphics g) {
        if (!drawBorder) return;

        int width = getSize().width;
        int height = getSize().height;

        g.setColor(Color.black);
        for (int i = 0; i < borderSize; i++) {
            g.drawRect(i, i, (width - i * 2 - 1), (height - i * 2 - 1));
        }
    }
}
