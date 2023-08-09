/**
 * AbstractFilterController.java
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

import java.util.*;

/** This abstract class handles maintaining the filter listeners.  It stores them
 *  in a vector and implements the methods to add and remove listeners.
 *  It also provides utility methods to notify all listeners for lineFiltered
 *  and filteringComplete events.  Subclasses need only implement run()
 *  @since 0.1
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public abstract class AbstractFilterController implements FilterController, FilterListener {

    /** A vector of FilterListener classes interested in filtered lines */
    private Vector listeners;

    /** Create the listeners vector */
    public AbstractFilterController() {
        listeners = new Vector();
    }

    /** Add a FilterListener to receive filtered lines
     *  @param listener The FilterListener to be notified
     */
    public void addListener(FilterListener listener) {
        listeners.addElement(listener);
    }

    /** Remove a FilterListener from receiving filtered lines
     *  @param listener The FilterListener to remove
     */
    public void removeListener(FilterListener listener) {
        listeners.removeElement(listener);
    }

    /** Notifies all listeners when a line has been filtered
     *  @param line The filtered line
     */
    public void lineFiltered(String line) {
        Enumeration e = listeners.elements();
        while (e.hasMoreElements()) {
            FilterListener l = (FilterListener) e.nextElement();
            l.lineFiltered(line);
        }
    }

    /** Notifies all listeners when filtering is complete
     *  @param success Flag indicating whether or not filtering was successful or not
     *  @param ex The exception that occurred if !success
     */
    public void filteringComplete(boolean success, Exception ex) {
        Enumeration e = listeners.elements();
        while (e.hasMoreElements()) {
            FilterListener l = (FilterListener) e.nextElement();
            l.filteringComplete(success, ex);
        }
    }
}