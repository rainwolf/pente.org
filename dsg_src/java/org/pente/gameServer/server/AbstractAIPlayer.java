/** AbstractAIPlayer.java
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

package org.pente.gameServer.server;

/** Convenience methods for AI players implementing AIPlayer.
 *  Provides simple methods to handle the thinking state
 *  of the AI player.
 *  @see MarksAIPlayer for example usage
 */
public abstract class AbstractAIPlayer implements AIPlayer {

    private boolean thinking;
    private Thread thinkingThread;
    
    /** Call this when your AI is starting to think */
    protected synchronized void startThinking() {
        thinking = true;
        thinkingThread = Thread.currentThread();
    }

    /** Called by the server to force your AI to stop thinking */
    public synchronized void stopThinking() {
        thinking = false;
        if (thinkingThread != null) {
            thinkingThread.interrupt();
            thinkingThread = null;
        }
    }

    /** Make your AI player call this every so often during long
     *  running processing so that the AI can stop thinking.
     */
    protected synchronized void checkStopped() throws InterruptedException {
        if (!thinking) {
            throw new InterruptedException();
        }
    }
}