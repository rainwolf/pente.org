package org.pente.gameServer.event;

import java.io.Serializable;

public class ClientInfo implements Serializable {

    private String browser;
    private String javaVersion;
    private String javaClassVersion;
    private String os;
    private String osVersion;

    public String toString() {
        return "[browser=" + browser + " java.version=" + javaVersion +
                " java.class.version=" + javaClassVersion + " os=" + os +
                " os.version=" + osVersion + "]";
    }

    public String getBrowser() {
        return browser;
    }

    public String getJavaClassVersion() {
        return javaClassVersion;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public String getOs() {
        return os;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public void setBrowser(String string) {
        browser = string;
    }

    public void setJavaClassVersion(String string) {
        javaClassVersion = string;
    }

    public void setJavaVersion(String string) {
        javaVersion = string;
    }

    public void setOs(String string) {
        os = string;
    }

    public void setOsVersion(String string) {
        osVersion = string;
    }
}
