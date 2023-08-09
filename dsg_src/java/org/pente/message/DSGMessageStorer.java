package org.pente.message;

import java.util.*;

public interface DSGMessageStorer {

    public void createMessage(DSGMessage message) throws DSGMessageStoreException;

    public void createMessage(DSGMessage message, boolean ccSender) throws DSGMessageStoreException;

    public void readMessage(DSGMessage message) throws DSGMessageStoreException;

    public void deleteMessage(int mid) throws DSGMessageStoreException;

    public DSGMessage getMessage(int mid) throws DSGMessageStoreException;

    public List<DSGMessage> getMessages(long pid) throws DSGMessageStoreException;

    public List<DSGMessage> getNextMessages(long pid, long start) throws DSGMessageStoreException;

    public int getNumNewMessages(long pid) throws DSGMessageStoreException;
}
