package org.pente.gameServer.core;

import org.pente.game.ChainedException;

/**
 * Created by waliedothman on 22/01/2017.
 */
public class DSGFollowerStoreException extends ChainedException {

    public DSGFollowerStoreException(String message) {
        super(message);
    }
    public DSGFollowerStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}