/**
 * HttpFilterController.java
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

package org.pente.filter.http;

import java.io.*;
import java.net.*;
import java.util.*;

import org.apache.log4j.*;

import org.pente.filter.*;

/** This class implements a FilterController that provides the output
 *  to be filtered from a http server.
 *  @since 0.1
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public class HttpFilterController extends AbstractFilterController {

    private static Category cat = Category.getInstance(HttpFilterController.class.getName());

    /** The host the http server is running on */
    protected String host;

    /** The request string to send to the http server */
    protected String requestString;

    /** The line filter to use for filtering before returning filtered lines */
    protected LineFilter lineFilter;


    public static void main(String args[]) throws Exception {

        Socket socket = new Socket("localhost", 8080);
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
        InputStream in = socket.getInputStream();
        FileOutputStream out = new FileOutputStream("/dsg/dev/bin/iyt.html");

//        writer.write("GET /search?search_data_format_in=dsg.gameDatabase.SimpleGameStorerSearchFormat&search_data_format_out=dsg.gameDatabase.HtmlGameStorerSearchFormat&search_data=K10%2C HTTP/1.0\r\n\r\n");
//writer.write("GET /test_post.html HTTP/1.1\r\n\r\n");
        writer.write("GET /dsg_db/iyt/ HTTP/1.0\r\n\r\n");
        writer.flush();
        boolean write = true;
        byte[] bytes;
        while (true) {
            bytes = new byte[512];
            int len = in.read(bytes, 0, 512);

            //System.out.println(len);
            if (len == -1) {
                break;
            } else {

                if (write == true) {
                    System.out.write(bytes, 0, len);
                    out.write(bytes, 0, len);
                    out.flush();
                } else {
                    /*
                    String str = new String(bytes);
                    int index = str.indexOf("\r\n\r\n");
//System.out.println("index="+index);
//System.out.println("len="+len);
//System.out.println("start="+(index + 4));
//System.out.println("len="+(len-(index+4)));
                    if (index != -1) {
                        out.write(bytes, index + 4, len - (index + 4));
                        write = true;
                    }
                    */
                }
            }
        }

        out.close();

/*
        GZIPInputStream inputStream = new GZIPInputStream(socket.getInputStream());
        byte[] bytes;
        while (true) {
            bytes = new byte[512];
            int len = inputStream.read(bytes, 0, 512);
            if (len == -1) {
                break;
            }
            else {
                System.out.println(new String(bytes));
            }
        }
*/
        socket.close();
    }


    /** Create a new HttpFilterController but don't run it yet
     *  @param method The method to send to the http server
     *  @param host The host the http server is running on
     *  @param request The request to send to the http server
     *  @param params The parameters to send in the request to the http server
     *  @param cookies The cookies to send to the http server
     *  @param lineFilter The line filter to use for filtering the response
     */
    public HttpFilterController(String method, String host, String request,
                                Hashtable params, Hashtable cookies, LineFilter lineFilter) {

        this.host = host;
        this.lineFilter = lineFilter;

        // build request string
        String paramString = new String();
        int paramCount = 0;
        Enumeration pe = params.keys();
        while (pe.hasMoreElements()) {
            String name = (String) pe.nextElement();
            String value = (String) params.get(name);
            try {
                value = URLEncoder.encode(value, "UTF-8");
            } catch (UnsupportedEncodingException e) {
            }

            if (paramCount++ > 0) {
                paramString += "&";
            }
            paramString += name + "=" + value;
        }

        // build the cookie string
        String cookieString = new String();
        Enumeration ce = cookies.keys();
        while (ce.hasMoreElements()) {
            String name = (String) ce.nextElement();
            String value = (String) cookies.get(name);
            cookieString += HttpConstants.GET_COOKIE + ": " + name + "=" + value + HttpConstants.END_LINE;
        }
//System.out.println("cookieString="+cookieString);
        // if the get method, send parameters in QUERY_STRING
        if (method.equals("GET")) {
            if (!request.endsWith("?") && !params.isEmpty()) {
                request += "?";
            }
            request += paramString;
        }

        requestString = method + " " + request + " HTTP/1.0";

        // send cookies as a header
        if (!cookies.isEmpty()) {
            requestString += HttpConstants.END_LINE +
                    cookieString;
        }

        // if the post method, send parameters after other http headers
        // and set the content length as a header
        if (method.equals("POST") && !params.isEmpty()) {
            requestString += HttpConstants.END_LINE +
                    HttpConstants.CONTENT_LENGTH + ": " + paramString.length() + HttpConstants.END_LINE +
                    HttpConstants.END_LINE +
                    paramString + HttpConstants.END_LINE;
        } else {
            requestString += HttpConstants.END_LINE + HttpConstants.END_LINE;
        }

        System.out.println(requestString);
        //cat.info(requestString);
    }

    /** Perform the filtering */
    public void run() {

        Socket socket = null;
        BufferedWriter out = null;
        BufferedReader in = null;
        boolean success = true;
        Exception ex = null;

        try {
            // connect to the http server
            socket = new Socket(host, HttpConstants.HTTP_PORT);

            // get the socket streams
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // send the request to the http server
            out.write(requestString.toCharArray());
            out.flush();

            String line = null;

            // loop reading in response lines, filter them and notify
            // filter listeners
            while (true) {

                line = in.readLine();

                if (line == null) break;

                if (lineFilter != null) {
                    line = lineFilter.filterLine(line);
                }
                if (line != null) {
                    lineFiltered(line);
                }
            }

        } catch (Exception e) {
            ex = e;
            ex.printStackTrace();
            success = false;
        } finally {

            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                }
            }
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                }
            }
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                }
            }

            // notify filter listeners that filtering is done
            filteringComplete(success, ex);
        }
    }
}