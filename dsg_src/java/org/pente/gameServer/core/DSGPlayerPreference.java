package org.pente.gameServer.core;

import java.io.*;

public class DSGPlayerPreference implements Serializable {
    
    private String name;
    private Object value;
    
    public DSGPlayerPreference(String name, Object value) {
        this.name = name;
        this.value = value;
    }
    public String getName() {
        return name;
    }
    public Object getValue() {
        return value;
    }

    public void setName(String name) {
        this.name = name;
    }
    public void setValue(Object value) {
        this.value = value;
    }
    
    public String toString() {
        return "[" + name + "=" + value + "]";
    }
}
