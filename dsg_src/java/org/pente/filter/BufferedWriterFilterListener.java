/** BufferedWriterFilterListener.java
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

/** I wrote this class in order to flush the buffer periodically.
 *  Other than that its the same as WriterFilterListener.
 *  It should be possible to either do socket.setSendBufferSize()
 *  or create a BufferedWriter and use it in WriterFilterListener.
 *  However, neither of these seemed to work.
 *  @since 0.2
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public class BufferedWriterFilterListener extends WriterFilterListener {

    /** The buffer size to use */
    protected int				bufferSize;

    /** The current buffer size */
    protected int				currentBuffer;

    /** Use this constructor if you want the buffer to be flushed
     *  after each line.
     *  @param out The Writer to write lines to
     *  @param endLine A string to write at the end of each filtered line
     */
    public BufferedWriterFilterListener(Writer out, String endLine) {
        this(out, endLine, 0);
    }
    /** Use this constructor if you want to specify the size of the buffer
     *  @param out The Writer to write lines to
     *  @param endLine A string to write at the end of each filtered line
     *  @param bufferSize Flush the buffer after this many chars have been written
     */
    public BufferedWriterFilterListener(Writer out, String endLine, int bufferSize) {
        super(out, endLine);

        this.out = out;
        this.bufferSize = bufferSize;
    }

    /** Write the filtered line to the Writer, flush the buffer if necessary
     *  @param line The filtered line
     */
    public void lineFiltered(String line) {

        try {

            super.lineFiltered(line);

            currentBuffer += line.length() + 2;
            if (currentBuffer > bufferSize) {
                currentBuffer = 0;
                out.flush();
            }

        } catch(IOException ex) {
        }
    }
}