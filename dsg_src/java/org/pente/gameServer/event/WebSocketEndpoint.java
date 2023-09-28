package org.pente.gameServer.event;

import org.pente.gameServer.server.WebSocketConfigurator;

import javax.websocket.*;
import javax.websocket.server.ServerEndpointConfig;

public class WebSocketEndpoint extends Endpoint {

    WebSocketConfigurator wcfg;

    @Override
    public void onOpen(final Session session, EndpointConfig config) {
        ServerEndpointConfig scfg = (ServerEndpointConfig) config;
        wcfg = (WebSocketConfigurator) scfg.getConfigurator();
        wcfg.addSession(session);

        session.addMessageHandler((MessageHandler.Whole<String>) msg -> wcfg.receiveMessage(session, msg));
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        wcfg.removeSession(session);
        super.onClose(session, closeReason);
    }

    @Override
    public void onError(Session session, Throwable thr) {
        wcfg.removeSession(session);
        super.onError(session, thr);
    }
}