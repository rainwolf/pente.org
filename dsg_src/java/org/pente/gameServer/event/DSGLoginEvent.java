package org.pente.gameServer.event;

import org.pente.gameServer.core.*;

public class DSGLoginEvent extends AbstractDSGEvent {

    // send for login
	private String player;
	private String password;
    private ClientInfo clientInfo;
    
    private boolean guest;
    
    // returned on successful login
    private DSGPlayerData me;
    private ServerData serverData;

    public DSGLoginEvent() {		
	}
    public DSGLoginEvent(boolean guest, ClientInfo info) {
    	this.guest = true;
        setInfo(info);
    }

    public DSGLoginEvent(String player, String password, ClientInfo info) {

		setPlayer(player);
		setPassword(password);
        setInfo(info);
	}

	public void setPlayer(String player) {
		this.player = player;
	}
	public String getPlayer() {
		return player;
	}
	
	public void setPassword(String password) {
		this.password = password;
	}
	public String getPassword() {
		return password;
	}
    
    public ClientInfo getInfo() {
        return clientInfo;
    }
    public void setInfo(ClientInfo info) {
        this.clientInfo = info;
    }
    public DSGPlayerData getMe() {
        return me;
    }
    public void setMe(DSGPlayerData me) {
        this.me = me;
    }

    public void setServerData(ServerData serverData) {
        this.serverData = serverData;
    }
    public ServerData getServerData() {
        return serverData;
    }
    public String toString() {
        String str = "login " + (guest ? "guest" : getPlayer());
        if (clientInfo != null) {
            str += " " + clientInfo;
        }
        return str;
    }
	public boolean isGuest() {
		return guest;
	}
}

