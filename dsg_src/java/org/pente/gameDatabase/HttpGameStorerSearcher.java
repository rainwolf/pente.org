/**
 * HttpGameStorerSearcher.java
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

//import java.text.*;

import org.pente.filter.http.*;
import org.pente.game.*;

public class HttpGameStorerSearcher extends AbstractHttpStorer implements GameStorerSearcher {

    private static ObjectFormat requestFormat = new HttpObjectFormat(new SimpleGameStorerSearchRequestFormat());
    private static GameStorerSearchResponseFormat responseFormat = new SimpleGameStorerSearchResponseFormat();

    public HttpGameStorerSearcher(String host, int port, GameFormat gameFormat) {
        super(host, port, gameFormat);
    }

    public HttpGameStorerSearcher(String host, int port, GameFormat gameFormat,
                                  String context, String userName, String password) {
        super(host, port, gameFormat, context, userName, password);
        //System.out.println("password = " + password);
    }

    public void search(GameStorerSearchRequestData data, GameStorerSearchResponseData responseData) throws Exception {

        // set the response format to simple, it doesn't matter since calling client
        // won't need to know how the data was formatted
        data.setGameStorerSearchResponseFormat(SimpleGameStorerSearchResponseFormat.class.getName());

        // format the request into a buffer
        StringBuffer paramsBuffer = new StringBuffer();
        requestFormat.format(data, paramsBuffer);

        // create the http request, send/receive
        StringBuffer requestBuffer = createHttpRequest(paramsBuffer, HttpGameServer.SEARCH);
        StringBuffer responseBuffer = sendHttpRequest(requestBuffer);

        int status = getHttpResponseCode(responseBuffer.toString());
        if (status != HttpConstants.STATUS_OK) {
            throw new HttpGameStorerException(status + " - " + responseBuffer.toString());
        } else {
            // strip headers
            responseBuffer = getHttpResponse(responseBuffer);

            // parse results
            responseFormat.parse(responseData, responseBuffer);
        }
    }
}