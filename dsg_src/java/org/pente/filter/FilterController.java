/**
 * FilterController.java
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

/** FilterControllers are responsible for generating lines of input/output to
 *  filter.  Individual FilterControllers will differ in where they get
 *  their input/output.  For each line they should perform filtering
 *  (probably by calling filterLine() in LineFilter) and then notify all
 *  FilterListeners that a line has been filtered.  FilterControllers
 *  can be run in the current thread or a new thread because they also
 *  implement Runnable.
 *  @since 0.1
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public interface FilterController extends Runnable {

    /** Add a FilterListener to receive filtered lines
     *  @param listener The FilterListener to be notified
     */
    public void addListener(FilterListener l);

    /** Remove a FilterListener from receiving filtered lines
     *  @param listener The FilterListener to remove
     */
    public void removeListener(FilterListener l);

    /** Perform the filtering */
    public void run();
}