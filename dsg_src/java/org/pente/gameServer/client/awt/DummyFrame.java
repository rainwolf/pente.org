package org.pente.gameServer.client.awt;

import java.awt.*;
import java.applet.*;

/**
 * DummyFrame.java
 * Copyright (C) 2003 Dweebo's Stone Games (http://www.pente.org/)
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
public class DummyFrame extends Frame {

    private Applet applet;

    public DummyFrame(Applet applet) {
        this.applet = applet;
    }

    public void setDummyLocation() {
        setLocation(applet.getLocationOnScreen());
    }
}
