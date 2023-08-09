package org.pente.gameServer.server;

import org.pente.gameServer.event.WebSocketDSGEventHandler;

import javax.websocket.Session;
import javax.websocket.server.ServerEndpointConfig;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WebSocketConfigurator extends ServerEndpointConfig.Configurator {
    Map<String, WebSocketDSGEventHandler> sessionId2HandlerMap;
    Server server;

    public WebSocketConfigurator(Server server) {
        sessionId2HandlerMap = new ConcurrentHashMap<>();
        this.server = server;
    }

    public void addSession(Session session) {
        WebSocketDSGEventHandler handler = server.addPlayerWebSocketSession(session);
        sessionId2HandlerMap.put(session.getId(), handler);
    }

    public void removeSession(Session session) {
        WebSocketDSGEventHandler handler = sessionId2HandlerMap.get(session.getId());
        handler.handleError(null);
        sessionId2HandlerMap.remove(session.getId());
    }

    public void receiveMessage(Session session, String message) {
        WebSocketDSGEventHandler handler = sessionId2HandlerMap.get(session.getId());
        handler.readMessage(message);
    }
}
