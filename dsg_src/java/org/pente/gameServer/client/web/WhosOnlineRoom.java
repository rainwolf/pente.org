package org.pente.gameServer.client.web;

import java.util.*;
import org.pente.gameServer.core.*;

public class WhosOnlineRoom {

	private String name;
	private List<DSGPlayerData> players;
	
	public WhosOnlineRoom(String name, List<DSGPlayerData> players) {
		this.name = name;
		this.players = players;
	}
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public List<DSGPlayerData> getPlayers() {
		return players;
	}

}
