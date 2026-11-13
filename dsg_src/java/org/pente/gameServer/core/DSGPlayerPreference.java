package org.pente.gameServer.core;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DSGPlayerPreference implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Days a player must wait between changing their weekend days or timezone (one shared clock). */
    public static final int WEEKEND_CHANGE_COOLDOWN_DAYS = 90;
    /** Human-readable form of WEEKEND_CHANGE_COOLDOWN_DAYS for messages. */
    public static final String WEEKEND_CHANGE_COOLDOWN_TEXT = "3 months";

    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;

    private String name;
    private Object value;
    private Date lastUpdateDate;

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

    public Date getLastUpdateDate() {
        return lastUpdateDate;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public void setLastUpdateDate(Date lastUpdateDate) {
        this.lastUpdateDate = lastUpdateDate;
    }

    /**
     * Days left until the weekend days may change again, rounded up (minimum 1
     * while blocked). Returns 0 when they may change now. A null lastWeekendChange
     * (no stored date) allows it.
     */
    public static int weekendChangeDaysLeft(Date lastWeekendChange) {
        if (lastWeekendChange == null) {
            return 0;
        }
        long remaining = weekendChangeAllowedAt(lastWeekendChange) - System.currentTimeMillis();
        if (remaining <= 0) {
            return 0;
        }
        return (int) ((remaining + DAY_MILLIS - 1) / DAY_MILLIS);
    }

    /**
     * Returns the message to show when the weekend days may not change yet,
     * or null when they may. A null lastWeekendChange (no stored date) allows it.
     * The clock is shared with the timezone, see {@link #sharedClockLastChange}.
     */
    public static String weekendChangeCooldownError(Date lastWeekendChange) {
        return cooldownError("weekend days were", lastWeekendChange);
    }

    /** Same as {@link #weekendChangeCooldownError} but for the timezone. */
    public static String timezoneChangeCooldownError(Date lastChange) {
        return cooldownError("timezone was", lastChange);
    }

    private static String cooldownError(String what, Date last) {
        if (weekendChangeDaysLeft(last) == 0) {
            return null;
        }
        return "Your other settings were saved, but your " + what + " not changed: " +
                "weekend days and timezone share one limit of a single change every " +
                WEEKEND_CHANGE_COOLDOWN_TEXT + ", because they affect turn-based timeouts. " +
                "You can change either of them again on " +
                new SimpleDateFormat("EEE, MMM d, yyyy HH:mm z").format(
                        new Date(weekendChangeAllowedAt(last))) + ".";
    }

    /** Name of the preference whose last_update_date is the shared weekend/timezone clock. */
    public static final String SHARED_CLOCK_PREF = "weekend";

    /**
     * True when submitted is a real timezone change: non-empty after trimming and
     * different from the stored one. Missing or empty never counts.
     */
    public static boolean timezoneChanged(String stored, String submitted) {
        if (submitted == null || submitted.trim().isEmpty()) {
            return false;
        }
        return !submitted.trim().equals(stored == null ? "" : stored.trim());
    }

    /** Last change of weekend days or timezone (the weekend pref's date), or null if none recorded. */
    public static Date sharedClockLastChange(java.util.List<DSGPlayerPreference> prefs) {
        if (prefs != null) {
            for (DSGPlayerPreference p : prefs) {
                if (SHARED_CLOCK_PREF.equals(p.getName())) {
                    return p.getLastUpdateDate();
                }
            }
        }
        return null;
    }

    /**
     * The weekend preference to re-store so its last_update_date becomes now, keeping
     * the current weekend days (default {7, 1} when the player has no row).
     */
    public static DSGPlayerPreference sharedClockRestart(java.util.List<DSGPlayerPreference> prefs) {
        int[] weekend = new int[]{7, 1};
        if (prefs != null) {
            for (DSGPlayerPreference p : prefs) {
                if (SHARED_CLOCK_PREF.equals(p.getName())) {
                    weekend = (int[]) p.getValue();
                }
            }
        }
        return new DSGPlayerPreference(SHARED_CLOCK_PREF, weekend);
    }

    private static long weekendChangeAllowedAt(Date lastWeekendChange) {
        return lastWeekendChange.getTime() + WEEKEND_CHANGE_COOLDOWN_DAYS * DAY_MILLIS;
    }

    public String toString() {
        return "[" + name + "=" + value + "]";
    }
}
