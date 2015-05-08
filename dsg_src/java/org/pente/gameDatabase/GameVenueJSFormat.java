/** GameVenueJSFormat.java
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

package org.pente.gameDatabase;

import java.util.*;

import org.pente.game.*;

public class GameVenueJSFormat {

    private static final GameEventData      EMPTY_EVENT_DATA;
    private static final GameRoundData      EMPTY_ROUND_DATA;
    private static final GameSectionData    EMPTY_SECTION_DATA;

    private static final GameSiteData       ALL_SITE_DATA;
    private static final GameEventData      ALL_EVENT_DATA;
    private static final GameRoundData      ALL_ROUND_DATA;
    private static final GameSectionData    ALL_SECTION_DATA;

    static {
        EMPTY_EVENT_DATA = new SimpleGameEventData();
        EMPTY_EVENT_DATA.setName("-");
        EMPTY_ROUND_DATA = new SimpleGameRoundData("-");
        EMPTY_SECTION_DATA = new SimpleGameSectionData("-");
        EMPTY_EVENT_DATA.addGameRoundData(EMPTY_ROUND_DATA);
        EMPTY_ROUND_DATA.addGameSectionData(EMPTY_SECTION_DATA);

        ALL_SITE_DATA = new SimpleGameSiteData();
        ALL_SITE_DATA.setName(GameSiteData.ALL_SITES);
        ALL_EVENT_DATA = new SimpleGameEventData();
        ALL_EVENT_DATA.setName(GameEventData.ALL_EVENTS);
        ALL_ROUND_DATA = new SimpleGameRoundData(GameRoundData.ALL_ROUNDS);
        ALL_SECTION_DATA = new SimpleGameSectionData(GameSectionData.ALL_SECTIONS);
        ALL_SITE_DATA.addGameEventData(ALL_EVENT_DATA);
        ALL_EVENT_DATA.addGameRoundData(ALL_ROUND_DATA);
        ALL_ROUND_DATA.addGameSectionData(ALL_SECTION_DATA);
    }

    public GameVenueJSFormat() {
    }

    /** This is implemented in a pretty dumb way but it works...
     *  Recursion would probably work with less code.
     */
    public StringBuffer format(Vector treeData) {

        // clone data since we plan on adding new data to tree
        Vector newTreeData = new Vector();
        for (int i = 0; i < treeData.size(); i++) {
            GameTreeData t = (GameTreeData) treeData.get(i);
            if (t.getID() == GridStateFactory.CONNECT6 ||
                t.getID() == GridStateFactory.SPEED_CONNECT6 ||
                t.getID() == GridStateFactory.TB_CONNECT6) continue;
            
            newTreeData.addElement(t.clone());
        }
        treeData = newTreeData;

        for (int i = 0; i < treeData.size(); i++) {
            
            GameTreeData game = (GameTreeData) treeData.get(i);
            
            List<GameSiteData> sites = game.getGameSiteData();
            // game must have at least 1 site, no need for EMPTY_SITE
            sites.add(0, (GameSiteData) ALL_SITE_DATA.clone());
            
            for (int j = 0; j < sites.size(); j++) {
                GameSiteData site = sites.get(j);
    
                List<GameEventData> events = site.getGameEventData();
                if (events.size() == 0) {
                    events.add(0, (GameEventData) EMPTY_EVENT_DATA.clone());
                }
                else {
                    events.add(0, (GameEventData) ALL_EVENT_DATA.clone());
                }
    
                for (int k = 0; k < events.size(); k++) {
                    GameEventData e = events.get(k);
                    Vector rounds = e.getGameRoundData();
    
                    if (rounds.size() == 0) {
                        rounds.add(0, (GameRoundData) EMPTY_ROUND_DATA.clone());
                    }
                    else {
                        rounds.add(0, (GameRoundData) ALL_ROUND_DATA.clone());
                    }
    
                    for (int l = 0; l < rounds.size(); l++) {
                        GameRoundData r = (GameRoundData) rounds.get(l);
                        Vector sections = r.getGameSectionData();
    
                        if (sections.size() == 0) {
                            sections.insertElementAt(EMPTY_SECTION_DATA.clone(), 0);
                        }
                        else {
                            sections.insertElementAt(ALL_SECTION_DATA.clone(), 0);
                        }
                    }
                }
            }
        }

        StringBuffer gamesBuf = new StringBuffer("games = new Array(");
        StringBuffer sitesBuf = new StringBuffer("sites = new Array(");
        StringBuffer eventsBuf = new StringBuffer("events = new Array(");
        StringBuffer roundsBuf = new StringBuffer("rounds = new Array(");
        StringBuffer sectionsBuf = new StringBuffer("sections = new Array(");

        for (int i = 0; i < treeData.size(); i++) {
            GameTreeData gameTreeData = (GameTreeData) treeData.get(i);
            if (i != 0) {
                gamesBuf.append(", ");
                sitesBuf.append(", ");
                eventsBuf.append(", ");
                roundsBuf.append(", ");
                sectionsBuf.append(", ");
            }
            gamesBuf.append("\"" + gameTreeData.getName() + "\"");

            List siteData = gameTreeData.getGameSiteData();
            sitesBuf.append("new Array(");
            eventsBuf.append("new Array(");
            roundsBuf.append("new Array(");
            sectionsBuf.append("new Array(");

            for (int j = 0; j < siteData.size(); j++) {
    
                GameSiteData gameSiteData = (GameSiteData) siteData.get(j);
    
                if (j != 0) {
                    sitesBuf.append(", ");
                    eventsBuf.append(", ");
                    roundsBuf.append(", ");
                    sectionsBuf.append(", ");
                }
                sitesBuf.append("\"" + gameSiteData.getName() + "\"");
    
                List<GameEventData> eventData = gameSiteData.getGameEventData();
                eventsBuf.append("new Array(");
                roundsBuf.append("new Array(");
                sectionsBuf.append("new Array(");

                for (int k = 0; k < eventData.size(); k++) {
    
                    GameEventData gameEventData = (GameEventData) eventData.get(k);
    
                    if (k != 0) {
                        eventsBuf.append(", ");
                        roundsBuf.append(", ");
                        sectionsBuf.append(", ");
                    }
    
                    String eventName = gameEventData.getName();
                    eventName = (eventName == null) ? "-" : eventName;
                    eventsBuf.append("\"" + eventName + "\"");
    
                    roundsBuf.append("new Array(");
                    sectionsBuf.append("new Array(");
                    Vector roundData = gameEventData.getGameRoundData();

                    for (int l = 0; l < roundData.size(); l++) {
    
                        GameRoundData gameRoundData = (GameRoundData) roundData.get(l);
    
                        if (l != 0) {
                            roundsBuf.append(", ");
                            sectionsBuf.append(", ");
                        }
                        roundsBuf.append("\"" + gameRoundData.getName() + "\"");
    
                        Vector sectionData = gameRoundData.getGameSectionData();
                        sectionsBuf.append("new Array(");

                        for (int m = 0; m < sectionData.size(); m++) {
    
                            GameSectionData gameSectionData = (GameSectionData) sectionData.elementAt(m);
                            if (m != 0) {
                                sectionsBuf.append(", ");
                            }
                            sectionsBuf.append("\"" + gameSectionData.getName() + "\"");
                        }
    
                        sectionsBuf.append(")");
                    }
    
                    sectionsBuf.append(")");
                    roundsBuf.append(")");
                }
    
                sectionsBuf.append(")");
                roundsBuf.append(")");
                eventsBuf.append(")");
            }
            sectionsBuf.append(")");
            roundsBuf.append(")");
            eventsBuf.append(")");
            sitesBuf.append(")");
        }

        sectionsBuf.append(");");
        roundsBuf.append(");");
        eventsBuf.append(");");
        sitesBuf.append(");");
        gamesBuf.append(");");

        gamesBuf.append("\n");
        gamesBuf.append(sitesBuf.toString());
        gamesBuf.append("\n");
        gamesBuf.append(eventsBuf.toString());
        gamesBuf.append("\n");
        gamesBuf.append(roundsBuf.toString());
        gamesBuf.append("\n");
        gamesBuf.append(sectionsBuf.toString());

        return gamesBuf;
    }
}