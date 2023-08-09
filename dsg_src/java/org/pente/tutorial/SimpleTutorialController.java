package org.pente.tutorial;

/**
 * SimpleTutorialController.java
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

import java.util.*;

public class SimpleTutorialController implements TutorialController {

    private TutorialScreen screen;

    private Hashtable sections;
    private Vector sectionNames;
    private Vector currentSection;
    private TutorialStep currentStep;
    private int currentStepIndex;

    /**
     * Constructor for SimpleTutorialController.
     */
    public SimpleTutorialController() {

        sections = new Hashtable();
        sectionNames = new Vector(5);
    }

    public void setTutorialScreen(TutorialScreen screen) {
        this.screen = screen;
        screen.addTutorialActionListener(this);
    }

    /**
     * @see org.pente.tutorial.TutorialController#addSection(String)
     */
    public void addSection(String name) {
        Vector section = new Vector();
        sections.put(name, section);
        sectionNames.addElement(name);
    }

    public Enumeration getSections() {
        return sectionNames.elements();
    }

    /**
     * @see org.pente.tutorial.TutorialController#addStep(String, String, TutorialStep)
     */
    public void addStep(
            String sectionName,
            TutorialStep step) {

        Vector section = (Vector) sections.get(sectionName);
        if (section != null) {
            section.addElement(step);
        }
    }

    /**
     * @see org.pente.tutorial.TutorialController#switchSection(String)
     */
    public void switchSection(String name) {

        Vector section = (Vector) sections.get(name);
        if (section != null) {
            currentSection = section;
            currentStepIndex = 0;
            currentStep = (TutorialStep) section.elementAt(currentStepIndex);
            nextStep();
            screen.switchSection(name);
        }
    }

    /**
     * @see org.pente.tutorial.TutorialActionListener#moveMade(int, int)
     */
    public void moveMade(int x, int y) {

        if (currentStep != null) {
            currentStep.moveMade(x, y);
        }
    }

    /**
     * @see org.pente.tutorial.TutorialActionListener#popupClosed(String)
     */
    public void popupClosed(String message) {

        if (currentStep != null) {
            currentStep.popupClosed(message);
        }
    }

    /**
     * @see org.pente.tutorial.TutorialActionListener#nextStep()
     */
    public void nextStep() {

        if (currentSection == null) {
            return;
        }

        // end of section
        if (currentStepIndex == currentSection.size()) {
            // do what?
        } else {

            currentStep = (TutorialStep)
                    currentSection.elementAt(currentStepIndex++);

            screen.clear();
            screen.setThinkingPieceVisible(false);
            screen.switchGame(currentStep.getGame());
            currentStep.init(this, screen);

            screen.setStepLabel(currentStep.getName());
            screen.setStepNumber(currentStepIndex, currentSection.size());

            currentStep.go();
        }
    }

    /**
     * @see org.pente.tutorial.TutorialActionListener#prevStep()
     */
    public void prevStep() {

        if (currentSection == null) {
            return;
        }

        // end of section
        if (currentStepIndex == 1) {
            // do what?
        } else {

            currentStepIndex--;
            currentStep = (TutorialStep)
                    currentSection.elementAt(currentStepIndex - 1);

            screen.clear();
            screen.setThinkingPieceVisible(false);
            screen.switchGame(currentStep.getGame());

            screen.setStepLabel(currentStep.getName());
            screen.setStepNumber(currentStepIndex, currentSection.size());

            currentStep.init(this, screen);
            currentStep.go();
        }
    }
}