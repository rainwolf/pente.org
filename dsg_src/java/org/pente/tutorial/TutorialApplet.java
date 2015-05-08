/** TutorialApplet.java
 *  Copyright (C) 2003 Dweebo's Stone Games (http://www.pente.org/)
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
package org.pente.tutorial;

import java.awt.*;
import java.applet.Applet;

import org.pente.gameServer.client.awt.*;

public class TutorialApplet extends Applet {

    private TutorialScreen screen;
    private TutorialController controller;
    
    public void init() {
        
        controller = new SimpleTutorialBuilder().buildTutorial();

        screen = new SimpleTutorialScreen(controller.getSections());
        controller.setTutorialScreen(screen);
        
        setLayout(new BorderLayout());
        add(BorderLayout.CENTER, (Panel) screen);
    }
    
    public void start() {
        controller.switchSection("Rules");
    }
    
    public void stop() {
    }

    public void destroy() {
        screen.destroy();
    }
}
