/** HttpConstants.java
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

package org.pente.filter.http;

/** Useful constants used by HTTP
 *  @since 0.2
 *  @author dweebo (dweebo@www.pente.org)
 *  @version 0.2 02/12/2001
 */
public class HttpConstants {

    /** Non-instantiable constructor */
    private HttpConstants() {
    }

    /** Protocol header */
    public static final String 	HTTP = 				"http://";
    /** End line string */
    public static final String 	END_LINE = 			"\r\n";

    /** The default http web server port */
    public static final int     HTTP_PORT =         80;

    public static final int    STATUS_OK =          200;
    public static final int    STATUS_BAD_REQUEST = 400;
    public static final int    STATUS_NOT_FOUND =   404;
    public static final int    STATUS_SERVER_ERROR =500;

    /** http header specifying a cookie line */
    public static final String  GET_COOKIE =        "Cookie";
    /** http header specifying the browser to store a cookie */
    public static final String  SET_COOKIE =        "Set-Cookie";
    /** http header specifying the length of the request or response */
    public static final String  CONTENT_LENGTH =    "content-length";

    public static final String  CONTENT_LOCATION =  "content-location";

    public static final String  CONTENT_TYPE =      "content-type";

    public static final String  CONTENT_TYPE_TEXT = "text/plain";

    public static final String  CONTENT_TYPE_HTML = "text/html";

    public static final String  CONTENT_TYPE_GIF =  "image/gif";

    public static final String  CONTENT_TYPE_JS =   "application/x-javascript";
}