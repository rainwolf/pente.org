/** SimpleGameEventData.java
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

import java.io.Serializable;
import java.util.*;

import org.pente.gameServer.client.web.SiteStatsData;

/** Simple implementation of GameEventData
 *
 *  @author dweebo (dweebo@www.pente.org)
 */
public class SimpleGameEventData implements GameEventData, Serializable {

    /** The unique id for this event */
    private int     id;

    /** The name of this event */
    private String  name;
    
    /** The associated game */
    private int game;
    
    /** The list of rounds in this event */
    private Vector  rounds;

    private GameSiteData siteData;
    
    public GameSiteData getSiteData() {
		return siteData;
	}


	public void setSiteData(GameSiteData siteData) {
		this.siteData = siteData;
	}


	/** Create the empty list of rounds */
    public SimpleGameEventData() {
        rounds = new Vector();
    }


    /** Set the name of the event
     *  @param name The event name
     */
    public void setName(String name) {
        this.name = name;
    }

    /** Get the name of the event
     *  @return String The event name
     */
    public String getName() {
        return name;
    }

    public int getGame() {
        return game;
    }
    public void setGame(int game) {
        this.game = game;
    }

    /** Get the event id
     *  @return int The event id
     */
    public int getEventID() {
        return id;
    }

    /** Set the event id
     *  @param eventID The id of the event
     */
    public void setEventID(int eventID) {
        this.id = eventID;
    }
    
    /** Add a round to this event
     *  @param GameRoundData The round data
     */
    public void addGameRoundData(GameRoundData gameRoundData) {
        rounds.addElement(gameRoundData);
    }

    /** Get the list of rounds for this event
     *  @param Vector The list of rounds
     */
    public Vector getGameRoundData() {
        return rounds;
    }
    
    /** used since game event data reused for pente applet
     *  no need to send round info down to client
     */
    public void clearGameRoundData() {
        rounds.clear();
    }

    /** Create a copy of the data in this object in a new object
     *  @return Object A copy of this object.
     */
    public Object clone() {

        SimpleGameEventData cloned = new SimpleGameEventData();

        cloned.setName(getName());
        cloned.setEventID(getEventID());
        cloned.setGame(getGame());

        for (int i = 0; i < rounds.size(); i++) {
            GameRoundData r = (GameRoundData) rounds.elementAt(i);
            cloned.addGameRoundData((GameRoundData) r.clone());
        }

        return cloned;
    }
    
    public String toString() {
    	String eName = name;
		if (eName.length() > 28) {
			eName = eName.substring(0, 25) + "...";
		}
		eName = "<html><body><font size=-3>" + 
			eName + "</font></body></html>";
		return eName;
    }
}