package org.pente.subscriptions.apple;

import org.pente.subscriptions.play.PlayStoreException;

/**
 * Whether a sandbox transaction may be granted to this pid (spec 2026-10-15 §2 D5): its username is on the
 * allow-list file. DsgSandboxGate is the real implementation; tests use a set of pids.
 */
public interface AppleSandboxGate {

    boolean allows(long pid) throws PlayStoreException;
}
