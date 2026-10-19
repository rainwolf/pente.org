package org.pente.gameServer.core;

/** Names nobody may register: reserved fragments and the offensive-name filter. */
public final class ReservedNames {

    private ReservedNames() {
    }

    /**
     * True when the name (already lower case) is reserved or offensive.
     * A null filter skips the offensive check.
     */
    public static boolean isReserved(String lowerName, UsernameFilter filter) {
        // preserve the dweebo name!
        if (lowerName.indexOf("dwe") >= 0
                || lowerName.startsWith("mm")
                || lowerName.indexOf("mm_ai") >= 0
                || lowerName.indexOf("admin") >= 0
                || lowerName.indexOf("guest") >= 0) {
            return true;
        }
        return filter != null && filter.isOffensive(lowerName);
    }
}
