/**
 * XMLAIConfigurator.java
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

import java.io.*;
import java.util.*;

import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.*;

import org.apache.log4j.*;

import org.pente.game.*;
import org.pente.gameServer.core.*;

/** I don't really know what I'm doing with XML so this class
 *  is just my attempt at getting things working.  I wouldn't
 *  recommend reusing it!
 *
 *  Probably need to add more validation here, like not
 *  allowing 2 aiPlayer's with same name.
 */
public class XMLAIConfigurator
        extends DefaultHandler
        implements AIConfigurator {

    private static final Category log4j = Category.getInstance(
            XMLAIConfigurator.class.getName());

    private AIData aiData;
    private String optionName;
    private Collection<AIData> aiDataCollection;
    private String elementName;
    private StringBuffer textBuffer;

    public static void main(String args[]) throws Throwable {

        BasicConfigurator.configure();

        new XMLAIConfigurator().getAIData(args[0]);
    }

    public Collection<AIData> getAIData(String configFileStr) throws Throwable {

        if (aiDataCollection != null) {
            return aiDataCollection;
        }

        if (configFileStr == null) {
            throw new IllegalStateException(
                    "aiConfigFile location not set in System properties " +
                            "(make sure its configured in web.xml).");
        }

        aiDataCollection = new ArrayList<>();

        // Use the validating parser
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setValidating(true);

        // Parse the input
        SAXParser saxParser = factory.newSAXParser();
        saxParser.parse(new File(configFileStr), this);

        return aiDataCollection;
    }

    public void startElement(
            String namespaceURI,
            String sName, // simple name
            String qName, // qualified name
            Attributes attrs)
            throws SAXException {

        elementName = sName; // element name
        if ("".equals(elementName)) elementName = qName; // not namespaceAware
    }

    public void endElement(
            String namespaceURI,
            String sName,
            String qName)
            throws SAXException {

        if (textBuffer != null) {

            if ("name".equals(elementName)) {
                if (aiData != null) {
                    aiDataCollection.add(aiData);
                }
                aiData = new AIData();
                aiData.setName(textBuffer.toString());
            } else if ("className".equals(elementName)) {
                aiData.setClassName(textBuffer.toString());
            } else if ("numLevels".equals(elementName)) {
                try {
                    int numLevels = Integer.parseInt(textBuffer.toString());
                    aiData.setNumLevels(numLevels);
                } catch (NumberFormatException p) {
                    throw new SAXException("numLevels must be a number.", p);
                }
            } else if ("validGame".equals(elementName)) {
                int validGame = GridStateFactory.getGameId(textBuffer.toString());
                aiData.addValidGame(validGame);
            } else if ("optionName".equals(elementName)) {
                optionName = textBuffer.toString();
            } else if ("optionValue".equals(elementName)) {
                aiData.setOption(optionName, textBuffer.toString());
            }
        }
        textBuffer = null;
    }


    public void endDocument()
            throws SAXException {

        if (aiData != null) {
            aiDataCollection.add(aiData);
        }
    }

    public void characters(char buf[], int offset, int len)
            throws SAXException {

        String s = new String(buf, offset, len);
        if (textBuffer == null) {
            textBuffer = new StringBuffer(s);
        } else {
            textBuffer.append(s);
        }
    }


    // treat validation errors as fatal
    public void error(SAXParseException e)
            throws SAXParseException {
        log4j.error("error()", e);
        throw e;
    }


    // dump warnings too
    public void warning(SAXParseException err)
            throws SAXParseException {
        log4j.info("** Warning"
                + ", line " + err.getLineNumber()
                + ", uri " + err.getSystemId());
        log4j.info("   " + err.getMessage());
    }
}
