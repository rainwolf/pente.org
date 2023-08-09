/**
 * AIPlayerFactory.java
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

package org.pente.gameServer.server;

import java.util.*;

import org.apache.log4j.*;

import org.pente.gameServer.core.AIData;

public class AIPlayerFactory {

    private static Category log4j = Category.getInstance(
            AIPlayerFactory.class.getName());

    public static AIPlayer getAIPlayer(AIData aiData) {

        AIPlayer aiPlayer = null;

        try {
            Class c = Class.forName(aiData.getClassName());
            aiPlayer = (AIPlayer) c.newInstance();

            initAIPlayer(aiPlayer, aiData);

        } catch (ClassNotFoundException cnfe) {
            log4j.error("Problem getting AI player.", cnfe);
        } catch (IllegalAccessException iae) {
            log4j.error("Problem getting AI player.", iae);
        } catch (InstantiationException ie) {
            log4j.error("Problem getting AI player.", ie);
        } catch (ClassCastException cce) {
            log4j.error("Problem getting AI player.", cce);
        }

        return aiPlayer;
    }

    private static void initAIPlayer(AIPlayer aiPlayer, AIData aiData) {
        aiPlayer.setGame(aiData.getGame());
        aiPlayer.setLevel(aiData.getLevel());
        aiPlayer.setSeat(aiData.getSeat());

        for (Enumeration e = aiData.getOptionNames(); e.hasMoreElements(); ) {
            String name = (String) e.nextElement();
            String value = aiData.getOption(name);
            aiPlayer.setOption(name, value);

        }
        aiPlayer.init();
    }

    public static AIPlayer getAIPlayerThreaded(
            AIData aiData,
            ThreadedAIPlayerCallback callback) {

        AIPlayer aiPlayer = new ThreadedAIPlayer(getAIPlayer(aiData), callback);

        if (aiPlayer != null) {
            initAIPlayer(aiPlayer, aiData);
        }

        return aiPlayer;
    }
}
