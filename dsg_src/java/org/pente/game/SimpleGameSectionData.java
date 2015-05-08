/** SimpleGameSectionData.java
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

package org.pente.game;

/** Simple implementation of GameSectionData
 *
 *  @author dweebo (dweebo@www.pente.org)
 */
public class SimpleGameSectionData implements GameSectionData, java.io.Serializable {

    /** The name of this section */
    private String name;

    /** Create with this name
     *  @param name The name of this section
     */
    public SimpleGameSectionData(String name) {
        setName(name);
    }

    /** Set the name of the section
     *  @param name The section name
     */
    public void setName(String name) {
        this.name = name;
    }

    /** Get the name of the section
     *  @return String The section name
     */
    public String getName() {
        return name;
    }

    /** Create a copy of the data in this object in a new object
     *  @return Object A copy of this object.
     */
    public Object clone() {
        return new SimpleGameSectionData(getName());
    }
}