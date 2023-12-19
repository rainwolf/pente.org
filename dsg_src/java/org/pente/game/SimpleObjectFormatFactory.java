/**
 * SimpleObjectFormatFactory.java
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

import org.pente.gameDatabase.*;
import org.pente.filter.iyt.game.*;

/** Simple implementation of an ObjectFormatFactory
 *
 *  @author dweebo (dweebo@www.pente.org)
 */
public class SimpleObjectFormatFactory implements ObjectFormatFactory {

    /** Simple request format */
    private static final SimpleGameStorerSearchRequestFormat simpleRequestFormat = new SimpleGameStorerSearchRequestFormat();

    /** Simple resquest filter format */
    private static final SimpleGameStorerSearchRequestFilterFormat simpleRequestFilterFormat = new SimpleGameStorerSearchRequestFilterFormat();

    /** Simple response format */
    private static final SimpleGameStorerSearchResponseFormat simpleResponseFormat = new SimpleGameStorerSearchResponseFormat();

    /** Simple html response format */
    private SimpleHtmlGameStorerSearchResponseFormat simpleHtmlResponseFormat;

    /** PGN game format */
    private static final PGNGameFormat pgnGameFormat = new PGNGameFormat();

    /** IYT PGN game format */
    private static final IYTPGNGameFormat iytPGNGameFormat = new IYTPGNGameFormat();

    /** Each factory creates its own hashtable of formats since some formats may
     *  vary between instances, although many will be the same.
     */
    private Hashtable<String, ObjectFormat> formats;

    /** Create with default values for simple html response format */
    public SimpleObjectFormatFactory() {
        simpleHtmlResponseFormat = new SimpleHtmlGameStorerSearchResponseFormat();

        initFormats();
    }

    /** Create with specified values for simple html response format
     *  @param simpleHtmlResponseIndexUrl Needed by simple html response format
     *  @param simpleHtmlResponseFormatBasePath Needed by simple html response format
     *  @param simpleHtmlResponseFormatJsPath Needed by simple html response format
     *  @param simpleHtmlResponseFormatImagePath Needed by simple html response format
     *  @param simpleHtmlGameStats Needed by simple html response format
     */
    public SimpleObjectFormatFactory(String simpleHtmlResponseIndexUrl,
                                     String simpleHtmlResponseFormatBasePath,
                                     String simpleHtmlResponseFormatJsPath,
                                     String simpleHtmlResponseFormatImagePath,
                                     GameStats simpleHtmlGameStats) {

        simpleHtmlResponseFormat = new SimpleHtmlGameStorerSearchResponseFormat(
                simpleHtmlResponseIndexUrl,
                simpleHtmlResponseFormatBasePath,
                simpleHtmlResponseFormatJsPath,
                simpleHtmlResponseFormatImagePath,
                simpleHtmlGameStats);

        initFormats();
    }

    /** Add all formats to a vector for later searching */
    protected void initFormats() {

        formats = new Hashtable<>();

        formats.put(simpleRequestFormat.getClass().getName(), simpleRequestFormat);
        formats.put(simpleRequestFilterFormat.getClass().getName(), simpleRequestFilterFormat);
        formats.put(simpleResponseFormat.getClass().getName(), simpleResponseFormat);
        formats.put(simpleHtmlResponseFormat.getClass().getName(), simpleHtmlResponseFormat);
        formats.put(pgnGameFormat.getClass().getName(), pgnGameFormat);
        formats.put(iytPGNGameFormat.getClass().getName(), iytPGNGameFormat);
    }

    /** Create and return a format with the given class name
     *  Actually formats have already been created, just find the one the client
     *  wants and return it.
     *  @param className The FQN of the desired format class
     *  @return ObjectFormat The desired format object, null if not found
     */
    public ObjectFormat createFormat(String className) {

        if (className == null) {
            return null;
        }

        return (ObjectFormat) formats.get(className);
    }
}