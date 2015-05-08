package org.pente.gameServer.core;

import java.util.Date;

public class DSGReturnEmailData {

    private String messageId;
    private long pid;
    private String email;
    private Date sendDate;
    
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
    public String getMessageId() {
        return messageId;
    }
    
    public void setPid(long pid) {
        this.pid = pid;
    }
    public long getPid() {
        return pid;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    public String getEmail() {
        return email;
    }
    
    public void setSendDate(Date sendDate) {
        this.sendDate = sendDate;
    }
    public Date getSendDate() {
        return sendDate;
    }
}
