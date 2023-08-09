/**
 * ObjectFormatFactory.java
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

/** Interface to objects that know how to create ObjectFormat classes given
 *  the fully qualified name (FQN) of a class.  Using the interface allows
 *  different implementations of factories in different objects.  For example,
 *  one implementation might wish to keep static instances of all format objects
 *  it returns to save memory.  Another may decide it needs to return a new
 *  format object for every createFormat() request.
 *  @author dweebo
 *  @since 0.3
 *  @version 0.3
 */
public interface ObjectFormatFactory {

    /** Create an instance of an ObjectFormat implementing class.
     *  @param className The FQN of the implementing class.
     *  @return ObjectFormat An instance of an ObjectFormat class
     */
    public ObjectFormat createFormat(String className);
}