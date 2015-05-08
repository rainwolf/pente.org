package org.pente.tutorial;

/** InteractiveMoveTutorialStep.java
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

import org.pente.game.*;
import org.pente.gameServer.core.*;

public class InteractiveMoveTutorialStep 
    extends AbstractTutorialStep 
    implements InteractiveTutorialStep {

    private static final GridState state = new SimpleGomokuState();
    private static final GridCoordinates coordinates =
        new AlphaNumericGridCoordinates(19, 19);

    private String move;

    private InteractiveMoveLogic logic;
    
    public InteractiveMoveTutorialStep(InteractiveMoveLogic logic) {
        this.logic = logic;
    }

    public void init(TutorialController controller, TutorialScreen screen) {
        super.init(controller, screen);
        screen.setThinkingPieceVisible(true);
    }

    public InteractiveMoveTutorialStep(final String move) {        
        logic = new InteractiveMoveLogic() {
            public boolean isMoveValid(int x, int y) {
                return coordinates.getCoordinate(x, 18 - y).equals(move);
            }
        };
    }
    

    /**
     * @see org.pente.tutorial.TutorialActionListener#moveMade(int, int)
     */
    public void moveMade(int x, int y) {
        
        if (logic.isMoveValid(x, 18 - y)) {
            screen.addMove(state.convertMove(x, 18 - y));
            screen.setThinkingPieceVisible(false);
            screen.popup("That's right!");
        }
        else {
            screen.popup("Nope, try again.");
        }
    }

    /**
     * @see org.pente.tutorial.TutorialActionListener#popupClosed(String)
     */
    public void popupClosed(String message) {
        
        if (message.equals("That's right!")) {
            controller.nextStep();
        }
    }
}
