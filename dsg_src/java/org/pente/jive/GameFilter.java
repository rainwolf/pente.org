package org.pente.jive;

import com.jivesoftware.base.Filter;
import com.jivesoftware.base.FilterChain;

public class GameFilter implements Filter {

	public GameFilter() {

	}
	
	// format is [game]gid[/game]
	public String applyFilter(String string, int currentIndex, FilterChain chain) {
        if (string == null || string.length() == 0) {
            return string;
        }

        int start = 0;
        int end = 0;
        while (true) {
        	start = string.indexOf("[game]", end);
        	end = string.indexOf("[/game]", end);
        	if (start == -1 || end == -1) break;
        	
        	String str = string.substring(start + 6, end);
        	String html = "";
        	if (str.contains("m=")) {
        		String paramStr = string.substring(start + 6, end);
            	String params[] = paramStr.split(" ");
            	html = "<iframe src=/gameServer/viewGameEmbed.jsp?";
            	boolean colorFound = false;
            	for (String param : params) {
            		html += param + "&";
            		if (param.startsWith("color=")) {
            			colorFound = true;
            		}
            	}
            	if (!colorFound) {
            		html += "color=%23deecde";
            	}
            	html += " width=770 height=600 frameborder=0 marginheight=0 marginwidth=0 scrolling=no></iframe>";
        	}
        	else {
	        	html = "<iframe src=/gameServer/viewLiveGame?g=" + 
	        		str + "&w=770&h=590&e=1&color=%23deecde width=770 height=600 frameborder=0 marginheight=0 marginwidth=0 scrolling=no></iframe>";
        	}
        	
        	string = string.substring(0, start) +
        			 html + "<br>" +
        			 string.substring(end + 7);
        }
	
        return chain.applyFilters(currentIndex, string);
	}
	
	public String getName() {
		return "GameFilter";
	}
}
