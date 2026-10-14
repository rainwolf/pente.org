package org.pente.gameServer.core;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DSGPlayerPreference implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Days a player must wait between changing their weekend days. */
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
     */
    public static String weekendChangeCooldownError(Date lastWeekendChange) {
        if (weekendChangeDaysLeft(lastWeekendChange) == 0) {
            return null;
        }
        return "Your other preferences were saved, but weekend days can only be changed once every " +
                WEEKEND_CHANGE_COOLDOWN_TEXT + ". You can change them again on " +
                new SimpleDateFormat("EEE, MMM d, yyyy HH:mm z").format(
                        new Date(weekendChangeAllowedAt(lastWeekendChange))) + ".";
    }

    private static long weekendChangeAllowedAt(Date lastWeekendChange) {
        return lastWeekendChange.getTime() + WEEKEND_CHANGE_COOLDOWN_DAYS * DAY_MILLIS;
    }

    public String toString() {
        return "[" + name + "=" + value + "]";
    }
}
