package org.pente.gameServer.server;

import javax.servlet.ServletContext;

public final class ContextHolder {
    private ContextHolder() {

    }

    public static ServletContext servletContext;
}
