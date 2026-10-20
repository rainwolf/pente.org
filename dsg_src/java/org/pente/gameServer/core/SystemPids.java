package org.pente.gameServer.core;

/** Player ids of the server's own accounts. */
public final class SystemPids {

    /** The "system" account that sends site notices. */
    public static final long SYSTEM_PID = 23000000016237L;

    /** The "computer" account that plays AI games. */
    public static final long COMPUTER_PID = 23000000020606L;

    private SystemPids() {
    }
}
