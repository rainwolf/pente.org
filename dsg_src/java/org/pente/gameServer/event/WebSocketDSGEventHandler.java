package org.pente.gameServer.event;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerGameData;

import javax.websocket.Session;
import java.util.Vector;

public class WebSocketDSGEventHandler extends ServerSocketDSGEventHandler {

    Session session;
    Vector listeners = new Vector();
    
    public WebSocketDSGEventHandler(Session session) {
        this.session = session;
    }


    @Override
    public synchronized void eventOccurred(DSGEvent dsgEvent) {
        dsgEvent.setCurrentTime();

        DSGEventWrapper wrappedEvent = new DSGEventWrapper(dsgEvent);
        String jsonStr = wrappedEvent.getJSON();

        session.getAsyncRemote().sendText(jsonStr);
    }
    
    public void readMessage(String message) {
        Throwable t = null;
        try {
            DSGEventWrapper wrappedEvent = null;

            GsonBuilder gsonBuilder = new GsonBuilder();
            gsonBuilder.registerTypeAdapter(DSGPlayerData.class, new DSGPlayerDataAdapter());
            gsonBuilder.registerTypeAdapter(DSGPlayerGameData.class, new DSGPlayerGameDataAdapter());
            Gson gson = gsonBuilder.create();
            wrappedEvent = gson.fromJson(message, DSGEventWrapper.class);

            // Object obj = in.readObject();
            Object obj = null;
            if (wrappedEvent != null) {
                obj = wrappedEvent.getEncodedEvent();
            }
            if (obj == null) {
                handleError(null);
                return;
            }
            else if (!(obj instanceof DSGEvent)) {
                handleError(null);
                return;
            }
            else {
                notifyListeners((DSGEvent) obj);
            }
            // on any throwable stop the thread
        } catch (Throwable th) {
            t = th;
        }
        
        if (t != null) {
            handleError(t);
        }
    }

    @Override
    public void addListener(DSGEventListener dsgEventListener) {
        listeners.addElement(dsgEventListener);
    }
    @Override
    public void removeListener(DSGEventListener dsgEventListener) {
        listeners.removeElement(dsgEventListener);
    }

    public void notifyListeners(DSGEvent dsgEvent) {
        for (int i = 0; i < listeners.size(); i++) {
            ((DSGEventListener) listeners.elementAt(i)).eventOccurred(dsgEvent);
        }
    }
    
    @Override public void handleError(Throwable t) {
        super.handleError(t);    
    }


    @Override
    public String getHostAddress() {
        return "127.0.0.1";
//        return session.getUserProperties().get("javax.websocket.endpoint.remoteAddress").toString();
    }
}
