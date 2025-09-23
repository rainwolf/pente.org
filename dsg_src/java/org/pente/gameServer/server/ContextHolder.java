package org.pente.gameServer.server;

import jakarta.servlet.ServletContext;

public final class ContextHolder {
    private ContextHolder() {

    }

    public static ServletContext servletContext;
}
