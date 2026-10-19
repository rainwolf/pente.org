package org.pente.gameServer.kids.test;

import java.util.Date;

import junit.framework.TestCase;

import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.SimpleDSGPlayerData;
import org.pente.gameServer.kids.KidAccountRules;

/** Pure rules for kid accounts: who may manage, convert, approve, change. */
public class KidAccountRulesTest extends TestCase {

    public KidAccountRulesTest(String name) {
        super(name);
    }

    private static SimpleDSGPlayerData player(long pid) {
        SimpleDSGPlayerData p = new SimpleDSGPlayerData();
        p.setPlayerID(pid);
        p.setName("p" + pid);
        p.setStatus(DSGPlayerData.ACTIVE);
        p.setPlayerType(DSGPlayerData.HUMAN);
        p.setEmail("p" + pid + "@example.com");
        p.setEmailValid(true);
        return p;
    }

    private static SimpleDSGPlayerData kid(long pid, long parentPid) {
        SimpleDSGPlayerData p = player(pid);
        p.setParentPid(Long.valueOf(parentPid));
        return p;
    }

    public void testParentErrorKid() {
        assertEquals(KidAccountRules.KIDS_CANT_MANAGE,
            KidAccountRules.parentError(kid(2, 1), false));
        assertEquals(KidAccountRules.KIDS_CANT_MANAGE,
            KidAccountRules.parentError(kid(2, 1), true));
    }

    public void testParentErrorUnverifiedOnlyWhenAdding() {
        SimpleDSGPlayerData p = player(1);
        p.setEmailValid(false);
        assertEquals(KidAccountRules.VERIFY_EMAIL,
            KidAccountRules.parentError(p, true));
        assertNull(KidAccountRules.parentError(p, false));
    }

    public void testParentOk() {
        assertNull(KidAccountRules.parentError(player(1), true));
        assertNull(KidAccountRules.parentError(player(1), false));
    }

    public void testConversionErrors() {
        SimpleDSGPlayerData caller = player(1);
        assertEquals(KidAccountRules.PLAYER_NOT_FOUND,
            KidAccountRules.conversionError(caller, null, false));

        SimpleDSGPlayerData inactive = player(2);
        inactive.setStatus(DSGPlayerData.DEACTIVE);
        assertEquals(KidAccountRules.PLAYER_NOT_FOUND,
            KidAccountRules.conversionError(caller, inactive, false));

        SimpleDSGPlayerData guest = player(5001);
        guest.setGuest(true);
        assertEquals(KidAccountRules.PLAYER_NOT_FOUND,
            KidAccountRules.conversionError(caller, guest, false));

        assertEquals(KidAccountRules.CANT_ADD_SELF,
            KidAccountRules.conversionError(caller, player(1), false));

        SimpleDSGPlayerData admin = player(3);
        admin.setAdmin(true);
        assertEquals(KidAccountRules.CANT_CONVERT,
            KidAccountRules.conversionError(caller, admin, false));

        SimpleDSGPlayerData computer = player(4);
        computer.setPlayerType(DSGPlayerData.COMPUTER);
        assertEquals(KidAccountRules.CANT_CONVERT,
            KidAccountRules.conversionError(caller, computer, false));

        assertEquals(KidAccountRules.CANT_CONVERT,
            KidAccountRules.conversionError(caller, kid(5, 9), false));

        assertEquals(KidAccountRules.CANT_CONVERT,
            KidAccountRules.conversionError(caller, player(6), true));
    }

    public void testConversionOk() {
        assertNull(KidAccountRules.conversionError(
            player(1), player(2), false));
    }

    public void testConversionSilentlyRefused() {
        SimpleDSGPlayerData admin = player(3);
        admin.setAdmin(true);
        assertTrue(KidAccountRules.conversionSilentlyRefused(admin, false));
        assertTrue(KidAccountRules.conversionSilentlyRefused(kid(5, 9), false));
        assertTrue(KidAccountRules.conversionSilentlyRefused(player(6), true));
        assertTrue(!KidAccountRules.conversionSilentlyRefused(player(7), false));
        SimpleDSGPlayerData computer = player(4);
        computer.setPlayerType(DSGPlayerData.COMPUTER);
        assertTrue(!KidAccountRules.conversionSilentlyRefused(computer, false));
    }

    public void testOwnsKidTrue() {
        assertTrue(KidAccountRules.ownsKid(player(1), kid(2, 1)));
    }

    public void testOwnsKidOtherParent() {
        assertTrue(!KidAccountRules.ownsKid(player(1), kid(2, 9)));
    }

    public void testOwnsKidNotAKid() {
        assertTrue(!KidAccountRules.ownsKid(player(1), player(2)));
        assertTrue(!KidAccountRules.ownsKid(player(1), null));
    }

    public void testPasswordValid() {
        assertTrue(KidAccountRules.passwordValid("abcde"));
        assertTrue(KidAccountRules.passwordValid("Ab_12"));
        assertTrue(!KidAccountRules.passwordValid("abcd"));
        assertTrue(!KidAccountRules.passwordValid("abcdefghijklmnopq"));
        assertTrue(KidAccountRules.passwordValid("abcdefghijklmnop"));
        assertTrue(!KidAccountRules.passwordValid("ab cd!"));
        assertTrue(!KidAccountRules.passwordValid(null));
    }

    public void testWhitelistAddErrors() {
        SimpleDSGPlayerData k = kid(2, 1);
        assertEquals(KidAccountRules.PLAYER_NOT_FOUND,
            KidAccountRules.whitelistAddError(k, null));
        SimpleDSGPlayerData inactive = player(3);
        inactive.setStatus(DSGPlayerData.DEACTIVE);
        assertEquals(KidAccountRules.PLAYER_NOT_FOUND,
            KidAccountRules.whitelistAddError(k, inactive));
        SimpleDSGPlayerData guest = player(5001);
        guest.setGuest(true);
        assertEquals(KidAccountRules.PLAYER_NOT_FOUND,
            KidAccountRules.whitelistAddError(k, guest));
        assertEquals(KidAccountRules.CANT_APPROVE,
            KidAccountRules.whitelistAddError(k, k));
        assertNull(KidAccountRules.whitelistAddError(k, player(3)));
    }

    public void testProfileChangeError() {
        assertNull(KidAccountRules.profileChangeError(
            true, "a@b.com", "a@b.com", ""));
        assertNull(KidAccountRules.profileChangeError(
            true, "a@b.com", "", ""));
        assertEquals(KidAccountRules.PROFILE_MANAGED,
            KidAccountRules.profileChangeError(true, "a@b.com", "a@b.com", "newpass1"));
        assertEquals(KidAccountRules.PROFILE_MANAGED,
            KidAccountRules.profileChangeError(true, "a@b.com", "c@d.com", ""));
        assertNull(KidAccountRules.profileChangeError(
            false, "a@b.com", "c@d.com", "newpass1"));
    }

    public void testLinkExpired() {
        long day = 24L * 60 * 60 * 1000;
        Date now = new Date();
        assertTrue(!KidAccountRules.linkExpired(new Date(now.getTime() - 6 * day), now));
        assertTrue(KidAccountRules.linkExpired(new Date(now.getTime() - 8 * day), now));
    }

    public void testPasswordEmailTarget() {
        SimpleDSGPlayerData parent = player(1);
        SimpleDSGPlayerData k = kid(2, 1);
        SimpleDSGPlayerData adult = player(3);
        assertSame(parent, KidAccountRules.passwordEmailTarget(k, parent));
        assertSame(k, KidAccountRules.passwordEmailTarget(k, null));
        assertSame(adult, KidAccountRules.passwordEmailTarget(adult, parent));
    }

    public void testPasswordEmailLogLine() {
        SimpleDSGPlayerData parent = player(1);
        parent.setName("mum");
        parent.setEmail("mum@example.invalid");
        SimpleDSGPlayerData k = kid(2, 1);
        k.setName("kidx");
        assertEquals("Forgot password: name=kidx mailTo=mum mum@example.invalid",
            KidAccountRules.passwordEmailLogLine(k, parent));
    }

    public void testNameTakenText() {
        assertEquals("Requested name bob is already taken, please choose another.",
            KidAccountRules.nameTaken("bob"));
    }

    public void testNewKidDataIsUnverifiedKid() {
        SimpleDSGPlayerData parent = player(1);
        parent.setEmailVisible(true);
        parent.setTimezone("America/Chicago");
        SimpleDSGPlayerData k = KidAccountRules.newKidData(parent, "kidone", "ENC");
        assertEquals("kidone", k.getName());
        assertEquals("ENC", k.getPassword());
        assertEquals(Long.valueOf(1), k.getParentPid());
        assertEquals(parent.getEmail(), k.getEmail());
        assertTrue(!k.getEmailValid());
        assertTrue(!k.isMobileAdult());
        assertTrue(!k.getEmailVisible());
        assertEquals("America/Chicago", k.getTimezone());
        assertEquals(DSGPlayerData.UNKNOWN, k.getSex());
        assertEquals(0, k.getAge());
        assertEquals(DSGPlayerData.HUMAN, k.getPlayerType());
        assertEquals(DSGPlayerData.ACTIVE, k.getStatus());
        assertNotNull(k.getRegisterDate());
    }

    public void testKidEmailNeverVisible() {
        assertTrue(!KidAccountRules.emailVisibleFor(true, true));
        assertTrue(KidAccountRules.emailVisibleFor(false, true));
        assertTrue(!KidAccountRules.emailVisibleFor(false, false));
    }
}
