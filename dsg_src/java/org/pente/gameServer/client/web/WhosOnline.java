package org.pente.gameServer.client.web;

import java.util.*;
import org.pente.gameServer.core.*;
import org.pente.gameServer.server.*;

public class WhosOnline {

	public static List<WhosOnlineRoom> getPlayers(
		Resources resources,
		SessionListener sessionListener) {

		ActivityLogger activityLogger = resources.getActivityLogger();
		DSGPlayerStorer dsgPlayerStorer = resources.getDsgPlayerStorer();
		
		List<WhosOnlineRoom> rooms = new ArrayList<WhosOnlineRoom>();
		//Map<String, List<DSGPlayerData>> players = new HashMap<String, List<DSGPlayerData>>();
		List<String> seen = new ArrayList<String>();
		
		try {
			ActivityData d[] = activityLogger.getPlayers(); 
	
			//sort into sections by room name, pname (serverid)
			Arrays.sort(d, new Comparator() {
			    public int compare(Object o1, Object o2) {
			        ActivityData d1 = (ActivityData) o1;
			        ActivityData d2 = (ActivityData) o2;
			        if (d1.getServerId() != d2.getServerId()) {
			            return (int) (d1.getServerId() - d2.getServerId());
			        }
			        else {
			            return d1.getPlayerName().compareTo(d2.getPlayerName());
			        }
			    }
			});
			long currentServerId = -1;
	    	ServerData sd = null;
	    	WhosOnlineRoom room = null;
			for (int i = 0; i < d.length; i++) {
	
	            if (d[i].getServerId() != currentServerId) {
	            	currentServerId = d[i].getServerId();
	            	sd = resources.getServerData((int) currentServerId);
	            	room = new WhosOnlineRoom(sd.getName(),  new ArrayList<DSGPlayerData>());
	            	rooms.add(room);
	            }
	            DSGPlayerData dsgPlayerData = null;
	            if (d[i].getPlayerName().startsWith("guest")) {
	            	dsgPlayerData = new SimpleDSGPlayerData();
	            	dsgPlayerData.setName(d[i].getPlayerName());
	            	dsgPlayerData.setGuest(true);
	            }
	            else {
	            	dsgPlayerData = dsgPlayerStorer.loadPlayer(d[i].getPlayerName());
	            }
	            room.getPlayers().add(dsgPlayerData);
	            seen.add(d[i].getPlayerName());
			}
	
			room = new WhosOnlineRoom("web", new ArrayList<DSGPlayerData>());
			rooms.add(room);
			List<String> names = sessionListener.getActivePlayers();
			Collections.sort(names);
			for (String name : names) {
				if (seen.contains(name)) continue;
				room.getPlayers().add(dsgPlayerStorer.loadPlayer(name));
			}
			
		} catch (DSGPlayerStoreException d) {
			d.printStackTrace();
		}
		
		return rooms;
	}
}
