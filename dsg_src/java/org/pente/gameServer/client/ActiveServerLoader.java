package org.pente.gameServer.client;

import java.io.*;
import java.util.*;
import java.net.*;

import org.pente.gameServer.core.ServerData;

public class ActiveServerLoader {

    public static void main(String args[]) throws Throwable {
        getActiveServers("localhost");
    }
    public static Vector getActiveServers(String host) throws IOException,
        MalformedURLException {

        URL url = new URL("https", host, "/gameServer/activeServers");
        BufferedReader in = new BufferedReader(new InputStreamReader(
            (InputStream) url.getContent()));
        Vector<ServerData> servers = new Vector<ServerData>();
        String s = null;
        while ((s = in.readLine()) != null) {
            ServerData d = new ServerData();
            int i = s.indexOf(' ');
            d.setPort(Integer.parseInt(s.substring(0, i)));
            d.setName(s.substring(i + 1));
            servers.addElement(d);
        }
        
        return servers;
    }
}
