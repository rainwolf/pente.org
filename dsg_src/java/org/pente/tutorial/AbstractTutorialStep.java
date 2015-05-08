/** AbstractTutorialStep.java
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

public abstract class AbstractTutorialStep implements TutorialStep {

    TutorialController controller;
    TutorialScreen screen;
    int game;
    
    public AbstractTutorialStep() {
        setGame(org.pente.game.GridStateFactory.PENTE); // default game
    }

    /**
     * @see org.pente.tutorial.TutorialStep#init(org.pente.tutorial.TutorialController, org.pente.tutorial.TutorialScreen)
     */
    public void init(TutorialController controller, TutorialScreen screen) {
        this.controller = controller;
        this.screen = screen;
    }

    /**
     * @see org.pente.tutorial.TutorialActionListener#nextStep()
     */
    public void nextStep() {
        controller.nextStep();
    }

    /**
     * @see org.pente.tutorial.TutorialActionListener#prevStep()
     */
    public void prevStep() {
        controller.prevStep();
    }
    
    public void switchSection(String name) {
    }

    /**
     * @see org.pente.tutorial.TutorialStep#go()
     */
    public void go() {
    }

    public void setGame(int game) {
        this.game = game;
    }
    public int getGame() {
        return game;
    }

    /**
     * @see org.pente.tutorial.TutorialStep#getName()
     */
    public String getName() {
        return null;
    }

    /**
     * @see org.pente.tutorial.TutorialActionListener#moveMade(int, int)
     */
    public void moveMade(int x, int y) {
    }

    /**
     * @see org.pente.tutorial.TutorialActionListener#popupClosed(java.lang.String)
     */
    public void popupClosed(String message) {
    }
}
