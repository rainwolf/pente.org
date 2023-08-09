package org.pente.admin;

public class LogData {

    private int line;
    private String event;

    public LogData(int line, String event) {
        this.line = line;
        this.event = event;
    }

    public String getEvent() {
        return event;
    }

    public int getLine() {
        return line;
    }

    public int getStartLine() {
        return line - line % 1000;
    }
}
