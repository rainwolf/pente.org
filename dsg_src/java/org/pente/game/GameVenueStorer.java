/** GameVenueStorer.java
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

import java.util.*;

/** Interface to a component that knows how to store and retrieve
 *  information about games, sites and events, useful for a database schema in which
 *  only id's are stored for the game/site/event for each game and the actual
 *  information about the game/site/event is stored in another table.
 *
 *  The other functionality is the ability to return a tree of game info.  The
 *  base of the tree is a vector of GameTreeData objects.  From these games
 *  you can walk down the tree through sites/events/rounds/sections to get every
 *  possible combination stored in the database.
 *
 *  @see org.pente.gameDatabase.GameVenueJSFormat
 *  @see org.pente.gameDatabase.GameVenueJSServlet
 *  @author dweebo (dweebo@www.pente.org)
 */
public interface GameVenueStorer {

    /** Get a vector of GameTreeData objects.  These GameTreeData objects are also
     *  loaded with the appropriate site data, and so on down to the section data.
     *  @return Vector The list of GameTreeData objects.
     */
    public Vector getGameTree();

    //public String getGameSiteData(int sid) throws Exception;
    public int getSiteID(String name) throws Exception;
    
    /** Get the data for a site from its unique id and associated game
     *  Use this when 
     *  @param game The game this site is associated with
     *  @param sid The unique site id for the site
     *  @return GameSiteData The data for the site
     *  @throws Exception If the game site data can't be retrieved
     */
    public GameSiteData getGameSiteData(int game, int sid) throws Exception;

    /** Get the data for a site from its name
     *  @param game The game this site is associated with
     *  @param name The name of the site
     *  @return GameSiteData The data for the site
     *  @throws Exception If the game site data can't be retrieved
     */
    public GameSiteData getGameSiteData(int game, String name) throws Exception;

    /** Add the data for a site to the storer.  Depending on the implementation
     *  this could mean store the data to a database, or store the data in memory, etc.
     *  Wherever the data is stored, subsequent calls to getGameSiteData() on the
     *  same instance must be able to return the data added with this method.
     *  @param game The game this site is for
     *  @param gameSiteData The data for the site
     *  @throws Exception If the game site data can't be added
     */
    public void addGameSiteData(int game, GameSiteData gameSiteData)
        throws Exception;


    /** Get the data for an event from its unique id.  Note that events are always
     *  associated with a site.  It is possible to have events from two sites with the
     *  same event name, but not the same event id.  Therefore it is necessary to
     *  specify the name of the site whenever dealing with events.
     *  @param game The game this event is associated with
     *  @param eid The unique event id for the event
     *  @param site The site this event is associated with
     *  @return GameEventData The data for the event
     *  @throws Exception If the game event data can't be retrieved
     */
    public GameEventData getGameEventData(int game, int eid, String site) throws Exception;

    /** Get the data for an event from its name.  Note that events are always
     *  associated with a site.  It is possible to have events from two sites with the
     *  same event name, but not the same event id.  Therefore it is necessary to
     *  specify the name of the site whenever dealing with events.
     *  @param game The game this event is associated with
     *  @param name The name of the event
     *  @param site The site this event is associated with
     *  @return GameEventData The data for the event
     *  @throws Exception If the game event data can't be retrieved
     */
    public GameEventData getGameEventData(int game, String name, String site) throws Exception;

    /** Add the data for an event to the storer.  Depending on the implementation
     *  this could mean store the data to a database, or store the data in memory, etc.
     *  Wherever the data is stored, subsequent calls to getGameEventData() on the
     *  same instance must be able to return the data added with this method.
     *  @param game The game this event is associated with
     *  @param gameEventData The data for the event
     *  @param site The site this event is associated with
     *  @throws Exception If the game event data can't be added
     */
    public void addGameEventData(int game, GameEventData gameEventData, String site) throws Exception;
}