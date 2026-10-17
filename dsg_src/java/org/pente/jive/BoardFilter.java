package org.pente.jive;

import com.jivesoftware.base.Filter;
import com.jivesoftware.base.FilterChain;
import com.jivesoftware.util.StringUtils;

public class BoardFilter implements Filter {

    public BoardFilter() {

    }

    // format is [board]g=Pente m=K10,L11[/board]
    public String applyFilter(String string, int currentIndex, FilterChain chain) {
        if (string == null || string.isEmpty()) {
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

            for (String param : params) {
                if (param.startsWith("gid=")) {
                    text = "<a href=/gameServer/viewLiveGame?g=" +
                            param.substring(4) +
                            ">Game ID " + param.substring(4) + "</a>";
                } else if (param.startsWith("m=")) {
                    text = param.substring(2);
                }
                link += param + "&";
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
