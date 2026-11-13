package org.pente.gameServer.core.test;

import junit.framework.*;
import org.pente.gameServer.core.DSGPlayerPreference;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Weekend days and timezone share one cooldown clock, the weekend pref's
 * last_update_date. The Player class below applies the same helper calls as
 * ChangeProfileServlet (timezone) and the prefs form (weekend) against an
 * in-memory pref list that stamps last_update_date on every store, as
 * MySQLDSGPlayerStorer / CacheDSGPlayerStorer do.
 */
public class SharedChangeCooldownTest extends TestCase {

    private static final long DAY = 24L * 60 * 60 * 1000;

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{SharedChangeCooldownTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(SharedChangeCooldownTest.class);
    }

    public SharedChangeCooldownTest(String name) {
        super(name);
    }

    static class Player {
        String timezone = "America/New_York";
        List<DSGPlayerPreference> prefs = new ArrayList<DSGPlayerPreference>();
        int updateDaysOffCalls = 0;

        void store(DSGPlayerPreference p, Date when) {
            p.setLastUpdateDate(when);
            for (int i = 0; i < prefs.size(); i++) {
                if (prefs.get(i).getName().equals(p.getName())) {
                    prefs.remove(i);
                    break;
                }
            }
            prefs.add(p);
        }

        /** Mirrors the "myInfo" branch; returns the refusal message or null. */
        String submitTimezone(String submitted) {
            if (!DSGPlayerPreference.timezoneChanged(timezone, submitted)) {
                return null;
            }
            String err = DSGPlayerPreference.timezoneChangeCooldownError(
                    DSGPlayerPreference.sharedClockLastChange(prefs));
            if (err != null) {
                return err;
            }
            timezone = submitted.trim();
            store(DSGPlayerPreference.sharedClockRestart(prefs), new Date());
            return null;
        }

        /** Mirrors the weekend part of the "prefs" branch. */
        String submitWeekend(int a, int b) {
            int[] stored = new int[]{7, 1};
            for (DSGPlayerPreference p : prefs) {
                if (p.getName().equals("weekend")) {
                    stored = (int[]) p.getValue();
                }
            }
            boolean differs = (a != stored[0] && a != stored[1]) || (b != stored[0] && b != stored[1]);
            if (!differs) {
                return null;
            }
            String err = DSGPlayerPreference.weekendChangeCooldownError(
                    DSGPlayerPreference.sharedClockLastChange(prefs));
            if (err != null) {
                return err;
            }
            store(new DSGPlayerPreference("weekend", new int[]{a, b}), new Date());
            updateDaysOffCalls++;
            return null;
        }
    }

    public void testTimezoneChangeStartsClockWithDefaultWeekend() {
        Player p = new Player();
        assertNull(p.submitTimezone("Asia/Tokyo"));
        assertEquals("Asia/Tokyo", p.timezone);
        Date d = DSGPlayerPreference.sharedClockLastChange(p.prefs);
        assertNotNull(d);
        assertTrue(DSGPlayerPreference.weekendChangeDaysLeft(d) > 0);
        int[] w = (int[]) p.prefs.get(0).getValue();
        assertEquals(7, w[0]);
        assertEquals(1, w[1]);
        assertEquals("no timeout recompute", 0, p.updateDaysOffCalls);
    }

    public void testTimezoneChangeKeepsExistingWeekendDays() {
        Player p = new Player();
        p.store(new DSGPlayerPreference("weekend", new int[]{6, 7}), new Date(System.currentTimeMillis() - 200 * DAY));
        assertNull(p.submitTimezone("Asia/Tokyo"));
        int[] w = (int[]) p.prefs.get(0).getValue();
        assertEquals(6, w[0]);
        assertEquals(7, w[1]);
        assertTrue(DSGPlayerPreference.weekendChangeDaysLeft(DSGPlayerPreference.sharedClockLastChange(p.prefs)) > 0);
    }

    public void testTimezoneRefusedInsideCooldown() {
        Player p = new Player();
        assertNull(p.submitTimezone("Asia/Tokyo"));
        String err = p.submitTimezone("Europe/Paris");
        assertNotNull(err);
        assertTrue(err.contains("timezone"));
        assertEquals("Asia/Tokyo", p.timezone);
    }

    public void testWeekendRefusedAfterTimezoneChange() {
        Player p = new Player();
        assertNull(p.submitTimezone("Asia/Tokyo"));
        String err = p.submitWeekend(5, 6);
        assertNotNull(err);
        assertTrue(err.contains("weekend days"));
        assertEquals(0, p.updateDaysOffCalls);
    }

    public void testTimezoneRefusedAfterWeekendChange() {
        Player p = new Player();
        assertNull(p.submitWeekend(5, 6));
        assertNotNull(p.submitTimezone("Asia/Tokyo"));
        assertEquals("America/New_York", p.timezone);
    }

    public void testChangeAllowedAfterCooldown() {
        Player p = new Player();
        p.store(new DSGPlayerPreference("weekend", new int[]{7, 1}),
                new Date(System.currentTimeMillis() - 91 * DAY));
        assertNull(p.submitTimezone("Asia/Tokyo"));
        assertEquals("Asia/Tokyo", p.timezone);
    }

    public void testUnchangedOrEmptyTimezoneNotCounted() {
        Player p = new Player();
        assertNull(p.submitTimezone("America/New_York"));
        assertNull(p.submitTimezone("  America/New_York  "));
        assertNull(p.submitTimezone(null));
        assertNull(p.submitTimezone(""));
        assertNull(p.submitTimezone("   "));
        assertTrue(p.prefs.isEmpty());
        assertEquals("America/New_York", p.timezone);
        // clock not started, so a weekend change is still allowed
        assertNull(p.submitWeekend(5, 6));
        assertEquals(1, p.updateDaysOffCalls);
    }

    public void testNullStoredTimezoneCountsAsChange() {
        assertTrue(DSGPlayerPreference.timezoneChanged(null, "UTC"));
        assertTrue(!DSGPlayerPreference.timezoneChanged(null, ""));
    }

    public void testRefusalMessagesReadCorrectly() {
        Date now = new Date();
        assertTrue(DSGPlayerPreference.timezoneChangeCooldownError(now).contains("your timezone was not changed"));
        assertTrue(DSGPlayerPreference.weekendChangeCooldownError(now).contains("your weekend days were not changed"));
    }

    public void testBlankSubmittedNeverCountsEvenWithBlankStored() {
        assertTrue(!DSGPlayerPreference.timezoneChanged("", ""));
        assertTrue(!DSGPlayerPreference.timezoneChanged(null, null));
        assertTrue(!DSGPlayerPreference.timezoneChanged("Custom/Zone", ""));
    }

    public void testNoDateAllowsChange() {
        assertNull(DSGPlayerPreference.timezoneChangeCooldownError(null));
        assertNull(DSGPlayerPreference.sharedClockLastChange(new ArrayList<DSGPlayerPreference>()));
    }
}
