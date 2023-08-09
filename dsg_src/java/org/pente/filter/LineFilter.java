/**
 * LineFilter.java
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

package org.pente.filter;

/** Interface for all classes interested in filtering lines.  The contract
 *  most LineFilters follow is to accept another LineFilter in a constructor
 *  and filter the line with this LineFilter before looking at the line.  In this
 *  way a chain of filters can be created.
 *  @since 0.1
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public interface LineFilter {

    /** Perform filtering on a line
     *  @param line The line to filter
     *  @return String The filtered line
     */
    public String filterLine(String line);
}