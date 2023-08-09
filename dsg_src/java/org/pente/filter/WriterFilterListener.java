/**
 * WriterFilterListener.java
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

import java.io.*;

/** FilterListener class that wraps an writer and writes
 *  and filtered lines to the writer.
 *  @since 0.2
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public class WriterFilterListener implements FilterListener {

    /** The Writer to write filtered lines to */
    protected Writer out;

    /** A string to write at the end of each filtered line */
    protected String endLine;

    /** Constructor
     *  @param out The Writer to write lines to
     *  @param endLine A string to write at the end of each filtered line
     */
    public WriterFilterListener(Writer out, String endLine) {
        this.out = out;
        this.endLine = endLine;
    }

    /** Write the filtered line to the Writer
     *  @param line The filtered line
     */
    public void lineFiltered(String line) {

        try {
            out.write(line);
            if (endLine != null) {
                out.write(endLine);
            }

        } catch (IOException ex) {
        }
    }

    /** Close the Writer
     *  @param success Whether or not the filtering was successful
     *  @param ex The exception that occurred if !success
     */
    public void filteringComplete(boolean success, Exception ex) {

        try {
            out.close();
        } catch (IOException ex2) {
        }
    }
}