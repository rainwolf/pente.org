/** HttpObjectFormat.java
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

import java.net.*;
import java.util.*;
import java.text.*;
import java.io.*;

import org.pente.filter.http.HttpUtilities;

/** Interface to to wrap another format into an http safe format that
 *  can be transferred without having to know anything about the actual format
 *  as long as it implements the ObjectFormat interface.
 *
 *  @author dweebo (dweebo@www.pente.org)
 */
public class HttpObjectFormat implements ObjectFormat {

    /** http param name for the value that holds the name of the base format */
    public static final String FORMAT_NAME = "format_name";

    /** http param name for the value that holds the data */
    public static final String FORMAT_DATA = "format_data";

    /** The underlying format class to wrap */
    private ObjectFormat        baseFormat;

    /** The object format factory used to create the underlying format class
     *  when parsing.
     */
    private ObjectFormatFactory formatFactory;

    /** Use this constructor if you don't need to call parse(), just format().
     *  @param baseFormat The ObjectFormat to wrap in format()
     */
    public HttpObjectFormat(ObjectFormat baseFormat) {
        this.baseFormat = baseFormat;
    }

    /** Use this constructor if you need to call just parse(), not format()
     *  @param formatFactory Used to create base formats in parse()
     */
    public HttpObjectFormat(ObjectFormatFactory formatFactory) {
        this.formatFactory = formatFactory;
    }

    /** Use this constructor is you need to call both parse() and format()
     *  @param baseFormat The ObjectFormat to wrap in format()
     *  @param formatFactory Used to create base formats in parse()
     */
    public HttpObjectFormat(ObjectFormat baseFormat, ObjectFormatFactory formatFactory) {
        this.baseFormat = baseFormat;
        this.formatFactory = formatFactory;
    }


    /** Format the data into a format that can be transferred via http
     *  @param data The data object to format
     *  @param buffer The buffer to place the formatted data into
     *  @return StringBuffer Same as passed in
     */
    public StringBuffer format(Object data, StringBuffer buffer) {

        formatName(buffer);
        buffer.append("&");
        formatData(data, buffer);

        return buffer;
    }

    /** Format the name of the base format class
     *  @param buffer The buffer to place the formatted data into
     *  @return StringBuffer Same as passed in
     */
    public StringBuffer formatName(StringBuffer buffer) {

        buffer.append(FORMAT_NAME);
        buffer.append("=");
        buffer.append(baseFormat.getClass().getName());

        return buffer;
    }

    /** Format the data
     *  @param data The data object to format
     *  @param buffer The buffer to place the formatted data into
     *  @return StringBuffer Same as passed in
     */
    public StringBuffer formatData(Object data, StringBuffer buffer) {

        buffer.append(FORMAT_DATA);
        buffer.append("=");
        StringBuffer baseBuffer = new StringBuffer();
        baseBuffer = baseFormat.format(data, baseBuffer);
        try {
            buffer.append(URLEncoder.encode(baseBuffer.toString(), "UTF-8"));
        } catch (UnsupportedEncodingException e) {
        }

        return buffer;
    }

    /** Parse the data into a data object
     *  @param data The data object to fill
     *  @param buffer The buffer to parse
     *  @return Object Same as passed in
     */
    public Object parse(Object data, StringBuffer buffer) throws ParseException {

        Hashtable params = new Hashtable();

        // parse the http params
        try {
            HttpUtilities.parseParams(buffer.toString(), params);
        } catch (Exception ex) {
            throw new ParseException("ParseException parsing http parameters", 0);
        }

        // get the underlying format name
        String formatName = (String) params.get(FORMAT_NAME);

        // get the data to parse with the underlying format
        String formatData = (String) params.get(FORMAT_DATA);

        // get a copy of the underlying format class
        ObjectFormat formatObject = formatFactory.createFormat(formatName);

        // parse the data with the underlying format
        return formatObject.parse(data, new StringBuffer(formatData));
    }
}