/** FileFilterController.java
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

package org.pente.filter;

import java.io.*;

/** This class implements a FilterController that provides the output
 *  to be filtered from a local file specified in the constructor.
 *  I wrote this class with testing filters in mind.
 *  @since 0.1
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public class FileFilterController extends AbstractFilterController {

    /** The file to read from */
    private File            file;

    /** The line filter to use for filtering */
    private LineFilter		lineFilter;

    /** Create a new FileFilterController
     *  @param File The file to use as output to be filtered
     *  @param LineFilter The filter to use
     */
    public FileFilterController(File file, LineFilter lineFilter) {
        this.file = file;
        this.lineFilter = lineFilter;
    }

    /** Perform the filtering */
    public void run() {

        // the file input reader
        BufferedReader 	in = null;
        // success flag to send to FilterListeners upon completion
        boolean			success = true;
        // the exception to send to FilterListeners upon completion
        Exception		ex = null;

        try {

            // open the file reader
            in = new BufferedReader(new FileReader(file));

            String line = null;

            // loop for each line of input
            while (true) {

                // get the next line from the file
                line = in.readLine();
                if (line == null) break;

                // filter the line
                if (lineFilter != null) {
                    line = lineFilter.filterLine(line);
                }

                // if the filter returned a value, notify the listeners
                if (line != null) {
                    lineFiltered(line);
                }
            }

        } catch (Exception e) {
            ex = e;
            success = false;
        } finally {

            if (in != null) {
                try { in.close(); } catch(IOException e) {}
            }
        }

        // notify listeners that filtering is complete
        filteringComplete(success, ex);
    }
}