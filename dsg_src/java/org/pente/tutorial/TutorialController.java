package org.pente.tutorial;

/**
 * TutorialController.java
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

import java.util.Enumeration;

public interface TutorialController extends TutorialActionListener {

    public void setTutorialScreen(TutorialScreen screen);

    public void addSection(String name);

    public void addStep(String sectionName, TutorialStep step);

    public void switchSection(String name);

    public Enumeration getSections();

}
