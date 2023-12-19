/**
 * SimpleGameSiteData.java
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

package org.pente.game;

import java.util.*;

/** Simple implementation of GameSiteData
 *
 *  @author dweebo (dweebo@www.pente.org)
 */
public class SimpleGameSiteData implements GameSiteData, java.io.Serializable {

    /** The unique id for this site */
    private int id;

    /** The name for this site */
    private String name;

    /** The short name for this site */
    private String shortName;

    /** The URL for this site */
    private String URL;

    /** The list of events in this site */
    private List<GameEventData> events;

    /** Create a new empty site */
    public SimpleGameSiteData() {
        this.events = new Vector<>();
    }


    /** Get the site id
     *  @return int The site id
     */
    public int getSiteID() {
        return id;
    }

    /** Set the site id
     *  @param siteID The site id
     */
    public void setSiteID(int siteID) {
        this.id = siteID;
    }

    /** Get the name of the site
     *  @return String The name of the site
     */
    public String getName() {
        return name;
    }

    public String toString() {
        return name;
    }

    /** Set the name of the site
     *  @param name The name of the site
     */
    public void setName(String name) {
        this.name = name;
    }


    /** Get the short name of the site
     *  @return String The short name of the site
     */
    public void setShortSite(String site) {
        this.shortName = site;
    }

    /** Set the short name of the site
     *  @param short name The name of the site
     */
    public String getShortSite() {
        return shortName;
    }


    /** Set the URL of the site
     *  @param URL The name of the site
     */
    public void setURL(String URL) {
        this.URL = URL;
    }

    /** Get the URL of the site
     *  @return String The URL of the site
     */
    public String getURL() {
        return URL;
    }


    /** Add a event to this site
     *  @param GameEventData The event data
     */
    public void addGameEventData(GameEventData gameEventData) {
        events.add(gameEventData);
        gameEventData.setSiteData(this);
    }

    /** Get the list of events for this event
     *  @param Vector The list of events
     */
    public List<GameEventData> getGameEventData() {
        return events;
    }


    /**
     * Create a copy of the data in this object in a new object
     *
     * @return Object A copy of this object.
     */
    public GameSiteData clone() {

        SimpleGameSiteData cloned = new SimpleGameSiteData();

        cloned.setName(getName());
        cloned.setShortSite(getShortSite());
        cloned.setSiteID(getSiteID());
        cloned.setURL(getURL());

        for (int i = 0; i < events.size(); i++) {
            GameEventData e = events.get(i);
            cloned.addGameEventData((GameEventData) e.clone());
        }

        return cloned;
    }
}