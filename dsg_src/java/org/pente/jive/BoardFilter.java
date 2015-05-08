package org.pente.jive;

import com.jivesoftware.base.Filter;
import com.jivesoftware.base.FilterChain;
import com.jivesoftware.util.StringUtils;

public class BoardFilter implements Filter {

	public BoardFilter() {

	}
	
	// format is [board]g=Pente m=K10,L11[/board]
	public String applyFilter(String string, int currentIndex, FilterChain chain) {
        if (string == null || string.length() == 0) {
            return string;
        }
        String origString = string;

        int start = 0;
        int end = 0;
        while (true) {
        	start = string.indexOf("[board]", end);
        	end = string.indexOf("[/board]", end);
        	if (start == -1 || end == -1) break;
        	
        	String paramStr = string.substring(start + 7, end);
        	String params[] = paramStr.split(" ");
        	String link = "<img src=/gameServer/board?";
        	String text = "";

        	for (int i = 0; i < params.length; i++) {
        		if (params[i].startsWith("gid=")) {
        			text = "<a href=/gameServer/viewLiveGame?g=" + 
        				params[i].substring(4) +
        				">Game ID " + params[i].substring(4) + "</a>";
        		}
        		else if (params[i].startsWith("m=")) {
        			text = params[i].substring(2);
        		}
        		link += params[i] + "&";
        	}

    		link += "w=500&h=440>";
    		
        	string = string.substring(0, start) +
        			 link + "<br>" + text + "<br>" +
        			 string.substring(end + 8);
        }
	
        return chain.applyFilters(currentIndex, string);
	}
	
	public String getName() {
		return "BoardFilter";
	}

}
