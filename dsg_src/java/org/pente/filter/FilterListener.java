/**
 * FilterListener.java
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

/** Interface for objects interested in receiving notifications when lines
 *  are filtered or filtering is complete.
 *  @since 0.1
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public interface FilterListener {

    /** Do something with a filtered line
     *  @param line The filtered line
     */
    public void lineFiltered(String line);

    /** Do something now that filtering is complete
     *  @param success Whether or not the filtering was successful
     *  @param ex The exception that occurred if !success
     */
    public void filteringComplete(boolean success, Exception ex);
}