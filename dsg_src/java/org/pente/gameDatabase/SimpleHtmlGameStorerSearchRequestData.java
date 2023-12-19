package org.pente.gameDatabase;

import java.net.*;
import java.util.*;
import java.io.*;

import org.pente.game.*;
import org.pente.filter.http.*;

public class SimpleHtmlGameStorerSearchRequestData extends SimpleGameStorerSearchRequestData {

    private int startZippedPartNum = 1;

    private static final String ZIPPED_PART_NUM_PARAM = "zippedPartNumParam";

    public SimpleHtmlGameStorerSearchRequestData() {
        super();
    }

    public SimpleHtmlGameStorerSearchRequestData(MoveData moveData) {
        super(moveData);
    }

    public SimpleHtmlGameStorerSearchRequestData(GameData gameData) {
        super(gameData);
    }


    public void setGameStorerSearchResponseParams(String paramString) {

        Hashtable<String, Object> params = new Hashtable<>();

        try {
            HttpUtilities.parseParams(paramString, params);
        } catch (Exception ex) {
            return;
        }

        String startZippedPartNumStr = (String) params.get(ZIPPED_PART_NUM_PARAM);
        if (startZippedPartNumStr != null) {
            startZippedPartNum = Integer.parseInt(startZippedPartNumStr);
        }
    }

    public String getGameStorerSearchResponseParams() {
        String params = null;
        try {
            params = URLEncoder.encode(
                    ZIPPED_PART_NUM_PARAM + "=" + startZippedPartNum, "UTF-8");
        } catch (UnsupportedEncodingException e) {
        }

        return params;
    }


    public int getStartZippedPartNum() {
        return startZippedPartNum;
    }
}