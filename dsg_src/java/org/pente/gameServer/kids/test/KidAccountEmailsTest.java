package org.pente.gameServer.kids.test;

import junit.framework.TestCase;

import org.pente.gameServer.kids.KidAccountEmails;

public class KidAccountEmailsTest extends TestCase {

    public KidAccountEmailsTest(String name) {
        super(name);
    }

    public void testCreatedBodyHasNameAndPassword() {
        String body = KidAccountEmails.createdBody("iostest", "kid_one", "secret1");
        assertTrue(body.startsWith("Dear iostest,"));
        assertTrue(body.indexOf("You created the kid account kid_one on Pente.org.") >= 0);
        assertTrue(body.indexOf("Name: kid_one") >= 0);
        assertTrue(body.indexOf("Password: secret1") >= 0);
        assertTrue(body.indexOf("kid_one can play anyone and can chat only with players you approve at "
                + "https://www.pente.org/gameServer/myprofile/kids") >= 0);
    }

    public void testConversionBodyHasLink() {
        String body = KidAccountEmails.conversionBody("kid_one", "iostest", "abc");
        assertTrue(body.indexOf("https://www.pente.org/confirmKidLink?code=abc") >= 0);
        assertTrue(body.indexOf("kid_one") >= 0);
        assertTrue(body.indexOf("iostest") >= 0);
        assertTrue(body.indexOf("7 days") >= 0);
        assertTrue(body.indexOf("ignore this email") >= 0);
    }

    public void testSubjects() {
        assertEquals("Pente.org kid account created", KidAccountEmails.createdSubject());
        assertEquals("Pente.org kid account request", KidAccountEmails.conversionSubject());
    }
}
