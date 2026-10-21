package org.pente.gameServer.kids.test;

import java.util.Arrays;
import java.util.Date;

import junit.framework.TestCase;

import org.pente.gameServer.kids.KidCopyEmails;

public class KidCopyEmailsTest extends TestCase {

    // 2026-10-19 14:03:00 UTC
    private static final Date WHEN = new Date(1792418580000L);

    public KidCopyEmailsTest(String name) {
        super(name);
    }

    public void testPmSubject() {
        assertEquals("Pente.org message copy: kid to friend: hi",
                KidCopyEmails.pmSubject("kid", "friend", "hi"));
    }

    public void testLiveChatLineFormat() {
        assertEquals("2026-10-19 14:03 UTC [table 3] graviton: hello",
                KidCopyEmails.liveChatLine(WHEN, "table 3", "graviton", "hello"));
    }

    public void testLiveChatBodyListsLinesInOrder() {
        String body = KidCopyEmails.liveChatBody("kid", Arrays.asList("one", "two"));
        assertEquals("Live game room chat that kid sent or saw:\n\none\ntwo\n\n"
                + "You get this copy because live chat copies are on for kid. "
                + "Change it at https://www.pente.org/gameServer/myprofile/kids", body);
        assertEquals("Pente.org live chat copy: kid", KidCopyEmails.liveChatSubject("kid"));
    }

    public void testPmBodyHasHeadersAndFooter() {
        String body = KidCopyEmails.pmBody("kid", "kid", "friend", WHEN, "hi", "hello there");
        assertEquals("From: kid\nTo: friend\nDate: 2026-10-19 14:03 UTC\nSubject: hi\n\n"
                + "hello there\n\n"
                + "You get this copy because message copies are on for kid. "
                + "Change it at https://www.pente.org/gameServer/myprofile/kids", body);
    }

    public void testTbLineFormat() {
        assertEquals("Move 12, 2026-10-19 14:03 UTC, graviton: good luck",
                KidCopyEmails.tbLine(12, WHEN, "graviton", "good luck"));
    }

    public void testTbLineWithoutDate() {
        assertEquals("Move 12, graviton: good luck",
                KidCopyEmails.tbLine(12, null, "graviton", "good luck"));
    }

    public void testTbSubject() {
        assertEquals("Pente.org game messages: kid vs graviton (Pente)",
                KidCopyEmails.tbSubject("kid", "graviton", "Pente"));
    }

    public void testTbBodyExact() {
        String body = KidCopyEmails.tbBody("kid", "kid", "graviton", "Pente", 50000000001234L,
                Arrays.asList("Move 1, kid: hi", "Move 2, graviton: gg"));
        assertEquals("Turn-based game messages between kid and graviton (Pente):\n\n"
                + "Move 1, kid: hi\nMove 2, graviton: gg\n\n"
                + "View the game: https://www.pente.org/gameServer/tb/game?gid=50000000001234&command=load\n\n"
                + "You get this copy because turn-based game message copies are on for kid. "
                + "Change it at https://www.pente.org/gameServer/myprofile/kids", body);
    }
}
