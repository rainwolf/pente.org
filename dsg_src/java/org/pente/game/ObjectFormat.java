/** ObjectFormat.java
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

import java.text.ParseException;

/** Interface to all the format classes.  Having all format classes implement
 *  the same interface allows a class like HttpObjectFormat to be able to wrap
 *  another format with an http format that can be transferred through http without
 *  having to know anything about the actual format.

 *  @author dweebo (dweebo@www.pente.org)
 *  @since 0.3
 *  @version 0.3
 */
public interface ObjectFormat {

    /** Format the object into a buffer
     *  @param obj The data object
     *  @param buffer The buffer to format into
     *  @return StringBuffer The buffer containing the formatted object
     */
    public StringBuffer format(Object obj, StringBuffer buffer);

    /** Parse the object from a buffer
     *  @param obj The object to populate with parsed data
     *  @param buffer The buffer to parse from
     *  @return Object The populated object
     *  @exception ParseException If the object cannot be parsed
     */
    public Object parse(Object obj, StringBuffer buffer) throws ParseException;
}