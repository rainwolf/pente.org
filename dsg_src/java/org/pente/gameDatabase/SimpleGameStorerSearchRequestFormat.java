/**
 * SimpleGameStorerSearchRequestFormat.java
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

package org.pente.gameDatabase;

import java.text.*;
import java.util.*;
import java.net.*;
import java.io.*;

import org.pente.game.*;
import org.pente.filter.http.*;

public class SimpleGameStorerSearchRequestFormat implements ObjectFormat {

    // tests format class
    public static void main(String args[]) throws Exception {

        GameStorerSearchRequestData requestData = new SimpleGameStorerSearchRequestData();
        requestData.addMove(180);
        requestData.addMove(200);
        requestData.setGameStorerSearchResponseFormat("org.pente.gameDatabase.SimpleHtmlGameStorerSearchResponseFormat");

        SimpleGameStorerSearchRequestFilterData filterData = new SimpleGameStorerSearchRequestFilterData();
        filterData.setStartGameNum(0);
        filterData.setEndGameNum(10);
        filterData.setPlayer1Name("dweebo");
        filterData.setPlayer2Name("mmammel");
        requestData.setGameStorerSearchRequestFilterData(filterData);

        StringBuffer buffer = new StringBuffer();
        SimpleGameStorerSearchRequestFormat requestFormat = new SimpleGameStorerSearchRequestFormat();
        requestFormat.format(requestData, buffer);

        System.out.println(buffer);

        GameStorerSearchRequestData requestData2 = new SimpleGameStorerSearchRequestData();
        requestFormat.parse(requestData2, buffer);

        StringBuffer buffer2 = new StringBuffer();
        requestFormat.format(requestData2, buffer2);

        System.out.println(buffer2);

        if (buffer.toString().equals(buffer2.toString())) {
            System.out.println("ok");
        }
    }

    private static final String paramSeparator = "&";
    private static final String moveDelimiter = ",";

    private static final String MOVES_PARAM = "moves";
    private static final String RESPONSE_FORMAT_PARAM = "response_format";
    private static final String RESPONSE_PARAMS = "response_params";
    private static final String FILTER_DATA_PARAM = "filter_data";
    private static final String RESULTS_ORDER = "results_order";

    private GameStorerSearchRequestData convertObject(Object obj) {

        if (obj == null) {
            return null;
        } else if (!(obj instanceof GameStorerSearchRequestData)) {
            throw new IllegalArgumentException("Object not GameStorerSearchRequestData");
        } else {
            return (GameStorerSearchRequestData) obj;
        }
    }

    public StringBuffer format(Object data, StringBuffer buffer) {

        GameStorerSearchRequestData requestData = convertObject(data);

        return format(requestData, buffer, true);
    }

    public StringBuffer format(
            GameStorerSearchRequestData requestData,
            StringBuffer buffer, boolean encode) {

        try {
            formatMoves(requestData, buffer, true, encode);
            buffer.append(paramSeparator);

            formatResponseFormat(requestData.getGameStorerSearchResponseFormat(), buffer, encode);
            buffer.append(paramSeparator);

            formatResponseParams(requestData.getGameStorerSearchResponseParams(), buffer, encode);
            buffer.append(paramSeparator);

            formatFilterData(requestData.getGameStorerSearchRequestFilterData(), buffer, true);
            buffer.append(paramSeparator);

            formatResponseOrder(requestData.getGameStorerSearchResponseOrder(), buffer);
        } catch (UnsupportedEncodingException e) {
        }

        return buffer;
    }

    public StringBuffer formatMoves(
            MoveData data, StringBuffer buffer, boolean param, boolean encode)
            throws UnsupportedEncodingException {

        if (param) {
            buffer.append(MOVES_PARAM);
            buffer.append("=");
        }

        StringBuffer movesBuf = new StringBuffer();
        for (int i = 0; i < data.getNumMoves(); i++) {
            movesBuf.append(PGNGameFormat.formatCoordinates(data.getMove(i)));
            movesBuf.append(",");
        }

        if (encode) {
            buffer.append(URLEncoder.encode(movesBuf.toString(), "UTF-8"));
        } else {
            buffer.append(movesBuf.toString());
        }

        return buffer;
    }

    public StringBuffer formatResponseFormat(
            String responseFormat, StringBuffer buffer, boolean encode)
            throws UnsupportedEncodingException {

        buffer.append(RESPONSE_FORMAT_PARAM);
        buffer.append("=");
        if (encode) {
            buffer.append(URLEncoder.encode(responseFormat, "UTF-8"));
        } else {
            buffer.append(responseFormat);
        }

        return buffer;
    }

    public StringBuffer formatResponseParams(
            String responseParams, StringBuffer buffer, boolean encode)
            throws UnsupportedEncodingException {

        buffer.append(RESPONSE_PARAMS);
        buffer.append("=");
        if (responseParams != null) {
            if (encode) {
                buffer.append(URLEncoder.encode(responseParams, "UTF-8"));
            } else {
                buffer.append(responseParams);
            }
        }

        return buffer;
    }

    public StringBuffer formatResponseOrder(int order, StringBuffer buffer) {

        buffer.append(RESULTS_ORDER);
        buffer.append("=");
        buffer.append(order);

        return buffer;
    }

    public StringBuffer formatFilterData(
            GameStorerSearchRequestFilterData filterData,
            StringBuffer buffer, boolean encode)
            throws UnsupportedEncodingException {

        buffer.append(FILTER_DATA_PARAM);
        buffer.append("=");

        SimpleGameStorerSearchRequestFilterFormat filterFormat = new SimpleGameStorerSearchRequestFilterFormat();
        StringBuffer buf = new StringBuffer();
        buf = filterFormat.format(filterData, buf, true);

        if (encode) {
            buffer.append(URLEncoder.encode(buf.toString(), "UTF-8"));
        } else {
            buffer.append(buf.toString());
        }

        return buffer;
    }

    public Object parse(Object data, StringBuffer buffer) throws ParseException {

        GameStorerSearchRequestData requestData = convertObject(data);

        Hashtable params = new Hashtable();

        try {
            HttpUtilities.parseParams(buffer.toString(), params);
        } catch (Exception ex) {
            throw new ParseException("ParseException parsing params", 0);
        }

        String moves = null;
        String responseFormat = null;
        String responseParams = null;
        String filterDataStr = null;
        int responseOrder = GameStorerSearchResponseMoveDataComparator.SORT_GAMES;
        String responseOrderStr = null;

        try {
            moves = (String) params.get(MOVES_PARAM);
            if (moves != null) {
                moves = URLDecoder.decode(moves, "UTF-8");
            }

            responseFormat = (String) params.get(RESPONSE_FORMAT_PARAM);
            if (responseFormat != null) {
                responseFormat = URLDecoder.decode(responseFormat, "UTF-8");
            }

            responseParams = (String) params.get(RESPONSE_PARAMS);
            if (responseParams != null) {
                responseParams = URLDecoder.decode(responseParams, "UTF-8");
            }

            filterDataStr = (String) params.get(FILTER_DATA_PARAM);
            if (filterDataStr != null) {
                filterDataStr = URLDecoder.decode(filterDataStr, "UTF-8");
            }

            responseOrderStr = (String) params.get(RESULTS_ORDER);
            if (responseOrderStr != null) {
                responseOrderStr = URLDecoder.decode(responseOrderStr, "UTF-8");
                responseOrder = Integer.parseInt(responseOrderStr);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            throw new ParseException("ParseException using URLDecoder", 0);
        }


        if (requestData == null) {
            if (responseFormat.equals(SimpleHtmlGameStorerSearchResponseFormat.class.getName())) {
                requestData = new SimpleHtmlGameStorerSearchRequestData();
            } else {
                requestData = new SimpleGameStorerSearchRequestData();
            }
        }

        requestData.setGameStorerSearchResponseFormat(responseFormat);
        requestData.setGameStorerSearchResponseParams(responseParams);
        requestData.setGameStorerSearchResponseOrder(responseOrder);

        // parse moves
        if (moves != null) {
            StringTokenizer moveTokenizer = new StringTokenizer(moves, moveDelimiter);
            while (moveTokenizer.hasMoreTokens()) {
                requestData.addMove(PGNGameFormat.parseCoordinates(moveTokenizer.nextToken()));
            }
        }

        // parse filter data
        if (filterDataStr != null) {
            SimpleGameStorerSearchRequestFilterFormat filterFormat = new SimpleGameStorerSearchRequestFilterFormat();
            SimpleGameStorerSearchRequestFilterData filterData = new SimpleGameStorerSearchRequestFilterData();
            filterData = (SimpleGameStorerSearchRequestFilterData) filterFormat.parse(filterData, new StringBuffer(filterDataStr));
            requestData.setGameStorerSearchRequestFilterData(filterData);
        }

        return requestData;
    }
}