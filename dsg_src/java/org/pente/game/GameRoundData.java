/**
 * GameRoundData.java
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

import java.util.*;

/** Interface for data structure to hold game round data.
 *  This interface provides a wrapper around the name of a round.  It also
 *  has methods to associate the sections played in this round and can be used
 *  to create a tree of venue information.
 *
 *  @see GameVenueStorer
 *  @see GameSectionData
 *  @see GameRoundData
 *  @author dweebo (dweebo@www.pente.org)
 */
public interface GameRoundData {

    /** Useful for representing the concept of all rounds in searches */
    public static final String ALL_ROUNDS = "All Rounds";


    /** Set the name of the round
     *  @param name The round name
     */
    public void setName(String name);

    /** Get the name of the round
     *  @return String The round name
     */
    public String getName();


    /** Add a section to this round
     *  @param GameSectionData The section data
     */
    public void addGameSectionData(GameSectionData gameSectionData);

    /** Get the list of sections for this round
     *  @param Vector The list of sections
     */
    public Vector<GameSectionData> getGameSectionData();


    /** Create a copy of the data in this object in a new object
     *  @return Object A copy of this object.
     */
    public Object clone();
}