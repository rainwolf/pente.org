package org.pente.gameServer.core.test;

import java.io.*;
import java.util.*;

import junit.framework.*;

import org.pente.gameServer.core.*;

public class UsernameFilterTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{
                UsernameFilterTest.class.getName()
        });
    }

    public static Test suite() {
        return new TestSuite(UsernameFilterTest.class);
    }

    public UsernameFilterTest(String name) {
        super(name);
    }

    private UsernameFilter small() {
        return new UsernameFilter(
                Arrays.asList("cunthead", "fuck", "slut", "=shit", "=rapist"));
    }

    private UsernameFilter shipped() throws IOException {
        UsernameFilter f = UsernameFilter.loadShipped();
        assertNotNull("shipped list must be on the classpath", f);
        return f;
    }

    public void testCleanNamePasses() {
        assertTrue(!small().isOffensive("rainwolf"));
        assertTrue(!small().isOffensive(null));
        assertTrue(!small().isOffensive(""));
    }

    public void testSubstringTierBlocksEmbeddedStem() {
        assertTrue(small().isOffensive("fuck"));
        assertTrue(small().isOffensive("mrfuckman"));
    }

    public void testLeetAndSeparatorVariantsBlocked() {
        UsernameFilter f = small();
        assertTrue(f.isOffensive("fu.c.k"));
        assertTrue(f.isOffensive("FUCK"));
        assertTrue(f.isOffensive("$h-i.t"));
        assertTrue(f.isOffensive("5h1t"));
        assertTrue(f.isOffensive("sh1t_head_not_listed"));
    }

    public void testExactTierMatchesWholeNameOrPart() {
        UsernameFilter f = small();
        assertTrue(f.isOffensive("shit"));
        assertTrue(f.isOffensive("SHIT"));
        assertTrue(f.isOffensive("shit123"));
        assertTrue(f.isOffensive("shit_99"));
        assertTrue(f.isOffensive("big_shit"));
        assertTrue(f.isOffensive("big.shit.guy"));
    }

    public void testExactTierIgnoresEmbeddedStem() {
        UsernameFilter f = small();
        assertTrue(!f.isOffensive("yoshitaka"));
        assertTrue(!f.isOffensive("shitake"));
        assertTrue(!f.isOffensive("therapist"));
        assertTrue(!f.isOffensive("the_therapist"));
    }

    public void testAmbiguousLeetReadings() {
        UsernameFilter f = small();
        assertTrue(f.isOffensive("s1ut"));
        assertTrue(f.isOffensive("fvck"));
        assertTrue(f.isOffensive("fvck_you"));
        assertTrue(f.isOffensive("5lvt"));
    }

    public void testNamesWithVAndOnePass() throws IOException {
        UsernameFilter f = shipped();
        assertTrue(!f.isOffensive("dave"));
        assertTrue(!f.isOffensive("vivian"));
        assertTrue(!f.isOffensive("kevin1"));
        assertTrue(!f.isOffensive("oliver"));
    }

    public void testManyAmbiguousCharsFallBackToSingleReading() {
        // 10 ambiguous chars exceed the cap; must not blow up and must
        // still apply the default reading
        UsernameFilter f = small();
        assertTrue(!f.isOffensive("vvvvvvvvvvvv"));
        assertTrue(f.isOffensive("vvvvvvvvvvvvfuck"));
    }

    public void testListFileCommentsAndBlanksIgnored() throws IOException {
        String bad = "# comment\n\n  \nbadword # trailing\n";
        UsernameFilter f = UsernameFilter.load(
                new ByteArrayInputStream(bad.getBytes("UTF-8")));
        assertEquals(1, f.getBlocklistSize());
        assertTrue(f.isOffensive("badword"));
        assertTrue(!f.isOffensive("comment"));
        assertTrue(!f.isOffensive("trailing"));
    }

    public void testShippedListLoadsFromClasspath() throws IOException {
        // getResourceAsStream, not a cwd-relative file: same path prod uses
        assertNotNull(UsernameFilter.class.getResourceAsStream(
                UsernameFilter.BLOCKLIST_RESOURCE));
        assertTrue(shipped().getBlocklistSize() > 10);
    }

    public void testShippedListBlocksOffensiveNames() throws IOException {
        UsernameFilter f = shipped();
        String[] bad = {"sh1t", "shit", "fuck_you", "n1gger", "nigger",
                "f4ggot", "c.u.n.t", "cunt69", "s1ut", "fvck", "fvck_you",
                "big_porn", "xxfuckxx", "shithead", "p3d0phile", "mthrfckr",
                "oldfckr505", "dipshit", "bullshit", "butthole", "dumbass",
                "bigcock420", "pentepenis", "theretard", "penteslut1",
                "pente_nazi", "pentehoe", "cock_69", "the_rapist"};
        for (int i = 0; i < bad.length; i++) {
            assertTrue(bad[i], f.isOffensive(bad[i]));
        }
    }

    public void testShippedListPassesRealNames() throws IOException {
        UsernameFilter f = shipped();
        String[] ok = {"rainwolf", "yoshitaka", "kinoshita", "matsushita",
                "yamashita", "shital", "nishit", "pushit", "pussycat",
                "retardant", "pornic", "whitler", "slutsky", "penisula",
                "scunthorpe", "therapist", "peniston", "classy_assassin",
                "cocktail", "dave", "vivian", "kevin1", "oliver", "josh1987",
                "vash127", "kobushit", "peniste", "pornchai", "tofuking",
                "dick_tate", "bonner14", "mrpenguin", "thehoek",
                "therapist_jane", "therapist.mom", "bigcocktail", "bigcockatoo"};
        for (int i = 0; i < ok.length; i++) {
            assertTrue(ok[i], !f.isOffensive(ok[i]));
        }
    }
}
