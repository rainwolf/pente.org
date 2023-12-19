/**
 * GameSectionData.java
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

package org.pente.game;

/** Interface for data structure to hold game section data.
 *  This interface provides a wrapper around the name of a section.
 *
 *  @see GameVenueStorer
 *  @see GameRoundData
 *  @author dweebo (dweebo@www.pente.org)
 */
public interface GameSectionData {

    /** Useful for representing the concept of all sections in searches */
    public static final String ALL_SECTIONS = "All Sections";

    /** Set the name of the section
     *  @param name The section name
     */
    public void setName(String name);

    /** Get the name of the section
     *  @return String The section name
     */
    public String getName();

    /**
     * Create a copy of the data in this object in a new object
     *
     * @return Object A copy of this object.
     */
    public GameSectionData clone();
}