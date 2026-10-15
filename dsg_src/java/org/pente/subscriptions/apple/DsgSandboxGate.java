package org.pente.subscriptions.apple;

import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerStoreException;
import org.pente.gameServer.core.DSGPlayerStorer;
import org.pente.subscriptions.play.PlayStoreException;

/** AppleSandboxGate over the live player storer: the pid's username must be on the allow-list file. */
public class DsgSandboxGate implements AppleSandboxGate {

    private final AppleSandboxAccounts accounts;
    private final DSGPlayerStorer playerStorer;

    public DsgSandboxGate(AppleSandboxAccounts accounts, DSGPlayerStorer playerStorer) {
        this.accounts = accounts;
        this.playerStorer = playerStorer;
    }

    public boolean allows(long pid) throws PlayStoreException {
        try {
            DSGPlayerData data = playerStorer.loadPlayer(pid);
            return data != null && accounts.contains(data.getName());
        } catch (DSGPlayerStoreException e) {
            throw new PlayStoreException("loadPlayer " + pid + " failed", e);
        }
    }
}
