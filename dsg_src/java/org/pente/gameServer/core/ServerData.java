package org.pente.gameServer.core;

import org.pente.game.*;
import java.io.Serializable;
import java.util.*;

public class ServerData implements Serializable {

    private int serverId;
    private String name;
    private int port;
    private boolean tournament;
    private boolean privateServer;
    
    private Vector gameEvents = new Vector();
    private Vector loginMessages = new Vector();
    
    private transient Vector players = new Vector();
    
    public boolean isPrivateServer() {
		return privateServer;
	}
	public void setPrivateServer(boolean privateServer) {
		this.privateServer = privateServer;
	}
	public int getServerId() {
        return serverId;
    }
    public void setServerId(int serverId) {
        this.serverId = serverId;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public int getPort() {
        return port;
    }
    public void setPort(int port) {
        this.port = port;
    }
    
    public boolean isTournament() {
        return tournament;
    }
    public void setTournament(boolean tournament) {
        this.tournament = tournament;
    }
    
    public void addGameEvent(GameEventData gameEvent) {
        gameEvents.addElement(gameEvent);
    }
    public Vector getGameEvents() {
        return gameEvents;
    }

    public Vector getLoginMessages() {
        return loginMessages;
    }
    public void addLoginMessage(String message) {
        loginMessages.addElement(message);
    }
    public void setLoginMessages(Vector loginMessages) {
        this.loginMessages = loginMessages;
    }
    
    public Vector getPlayers() {
    	return players;
    }
    public void addPlayer(String name) {
    	players.addElement(name);
    }
    public void removePlayer(String name) {
    	players.removeElement(name);
    }
    
    public String toString() {
        return "[" + serverId + "="+ name + ":" + port + ", " + tournament + "]";
    }
}
