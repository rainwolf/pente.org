package org.pente.gameServer.client.web;

public interface LoginAccessController {
    public boolean requiresLogin(String resource);

    public boolean isRestricted(String resource);

    public boolean requiresAdmin(String resource);
}

