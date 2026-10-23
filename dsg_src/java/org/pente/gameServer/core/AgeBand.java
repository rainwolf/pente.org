package org.pente.gameServer.core;

/** Age bands reported by the mobile apps (US app-store age laws). */
public final class AgeBand {

    public static final String UNDER_13 = "UNDER_13";
    public static final String AGE_13_15 = "13_15";
    public static final String AGE_16_17 = "16_17";
    public static final String ADULT = "18_PLUS";

    private AgeBand() {
    }

    /** Canonical band for a request value (case-insensitive), or null if invalid. */
    public static String parse(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (v.equals(UNDER_13) || v.equals(AGE_13_15)
            || v.equals(AGE_16_17) || v.equals(ADULT)) {
            return v;
        }
        return null;
    }

    public static boolean isAdult(String band) {
        return ADULT.equals(band);
    }
}
