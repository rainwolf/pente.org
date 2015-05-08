package org.pente.gameServer.client.web;

import java.util.*;

import javax.servlet.http.*;

import org.pente.game.*;

public class StatsData {

	private int		game = -1;
	private int		sortField = -1;
	private int		startNum = -1;
	private int		length = -1;
	private boolean	includeProvisional = false;
	private boolean includeInactive = false;
    
    public static final  int HUMAN = 0;
    public static final int AI = 1;
    public static final int BOTH = 2;
	private int     playerType = HUMAN;
	private int		numResults;
	private Vector	results;
	
	
	public void initialize(HttpServletRequest request) {

		String gameStr = request.getParameter("game");
		if (gameStr != null) {
			game = Integer.parseInt(gameStr);
		}
		String sortFieldStr = request.getParameter("sortField");
		if (sortFieldStr != null) {
			sortField = Integer.parseInt(sortFieldStr);
		}
		String startNumStr = request.getParameter("startNum");
		if (startNumStr != null) {
			startNum = Integer.parseInt(startNumStr);
		}
		String lengthStr = request.getParameter("length");
		if (lengthStr != null) {
			length = Integer.parseInt(lengthStr);
		}

		includeProvisional = request.getParameter("includeProvisional") != null;
		includeInactive = request.getParameter("includeUnactive") != null;
        
        String playerTypeStr = request.getParameter("playerType");
        if (playerTypeStr != null) {
            try {
                playerType = Integer.parseInt(playerTypeStr);
            } catch (NumberFormatException n) {
            }
        }
	}

	public boolean isValidSearch() {
		
		if (game < 1) {
			return false;
		}
		if (sortField < 0 || sortField > 8) {
			return false;
		}
		if (startNum < 0) {
			return false;
		}
		if (length < 0) {
			return false;
		}

		return true;
	}

	public int getGame() {
		return game;
	}

	public void setGame(int game) {
		this.game = game;
	}

	public int getSortField() {
		return sortField;
	}

	public void setSortField(int sortField) {
		this.sortField = sortField;
	}

	public int getStartNum() {
		return startNum;
	}

	public void setStartNum(int startNum) {
		this.startNum = startNum;
	}

	public int getLength() {
		return length;
	}

	public void setLength(int length) {
		this.length = length;
	}

	public boolean getIncludeProvisional() {
		return includeProvisional;
	}

	public void setIncludeProvisional(boolean includeProvisional) {
		this.includeProvisional = includeProvisional;
	}

	public boolean getIncludeInactive() {
		return includeInactive;
	}

	public void setIncludeInactive(boolean includeInactive) {
		this.includeInactive = includeInactive;
	}

	public int getNumResults() {
		return numResults;
	}

	public void setNumResults(int numResults) {
		this.numResults = numResults;
	}

	public Vector getResults() {
		return results;
	}

	public void setResults(Vector results) {
		this.results = results;
	}
    
    public int getPlayerType() {
        return playerType;
    }
}

