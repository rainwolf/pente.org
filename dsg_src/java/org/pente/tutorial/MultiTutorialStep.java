package org.pente.tutorial;

/** MultiTutorialStep.java
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

import java.util.*;

public class MultiTutorialStep extends AbstractTutorialStep {

    private Vector steps;
    
    public MultiTutorialStep() {
        steps = new Vector();
    }

    
    public void addTutorialStep(TutorialStep step) {
        steps.addElement(step);
    }
    
    public void go() {
        
        for (int i = 0; i < steps.size(); i++) {
            TutorialStep s = (TutorialStep) steps.elementAt(i);
            s.go();
        }
    }
    
    public void init(TutorialController controller, TutorialScreen screen) {

        for (int i = 0; i < steps.size(); i++) {
            TutorialStep s = (TutorialStep) steps.elementAt(i);
            s.init(controller, screen);
        }
    }

    
    /**
     * @see org.pente.tutorial.TutorialStep#getName()
     */
    public String getName() {

        if (steps.size() == 0) {
            return null;
        }

        TutorialStep s = (TutorialStep) steps.elementAt(0);
        return s.getName();
    }
    
    /**
     * @see org.pente.tutorial.TutorialActionListener#moveMade(int, int)
     */
    public void moveMade(int x, int y) {
        if (steps.size() == 0) {
            return;
        }

        TutorialStep s = (TutorialStep) steps.elementAt(steps.size() - 1);
        if (s instanceof InteractiveTutorialStep) {
            s.moveMade(x, y);
        }
    }

    /**
     * @see org.pente.tutorial.TutorialActionListener#popupClosed(java.lang.String)
     */
    public void popupClosed(String message) {
        if (steps.size() == 0) {
            return;
        }

        TutorialStep s = (TutorialStep) steps.elementAt(steps.size() - 1);
        if (s instanceof InteractiveTutorialStep) {
            s.popupClosed(message);
        }
    }
}
