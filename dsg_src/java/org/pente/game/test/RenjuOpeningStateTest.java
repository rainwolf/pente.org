package org.pente.game.test;

import junit.framework.*;
import org.pente.game.*;

public class RenjuOpeningStateTest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[]{RenjuOpeningStateTest.class.getName()});
    }

    public static Test suite() {
        return new TestSuite(RenjuOpeningStateTest.class);
    }

    public RenjuOpeningStateTest(String name) {
        super(name);
    }

    public void testConstants() {
        assertEquals(0, RenjuOpeningState.PENDING);
        assertEquals(1, RenjuOpeningState.NO);
        assertEquals(2, RenjuOpeningState.YES);
    }

    public void testEncodeDigitWeights() {
        RenjuOpeningState s = new RenjuOpeningState();
        s.swap1 = 2;
        assertEquals(2, s.encode());            // 2 * 3^0
        s = new RenjuOpeningState();
        s.swap5 = 1;
        assertEquals(243, s.encode());          // 1 * 3^5
        s = new RenjuOpeningState();
        s.branch = 2;
        assertEquals(162, s.encode());          // 2 * 3^4 = 162
    }

    public void testRoundTripExhaustive() {
        for (int packed = 0; packed <= 728; packed++) {
            RenjuOpeningState s = RenjuOpeningState.decode(packed);
            assertEquals(packed, s.encode());
        }
    }

    public void testDecodeExtractsDigits() {
        // swap1=1, swap2=2, swap3=0, swap4=1, branch=2, swap5=1
        int packed = 1 + 2 * 3 + 0 * 9 + 1 * 27 + 2 * 81 + 1 * 243;
        RenjuOpeningState s = RenjuOpeningState.decode(packed);
        assertEquals(1, s.swap1);
        assertEquals(2, s.swap2);
        assertEquals(0, s.swap3);
        assertEquals(1, s.swap4);
        assertEquals(2, s.branch);
        assertEquals(1, s.swap5);
    }

    public void testOfferByteRoundTrip() {
        int[] offers = {0, 7, 112, 224, 113, 99, 5, 200, 1, 150};
        byte[] bytes = RenjuOpeningState.encodeOffers(offers);
        assertEquals(10, bytes.length);
        int[] back = RenjuOpeningState.decodeOffers(bytes);
        assertEquals(offers.length, back.length);
        for (int i = 0; i < offers.length; i++) {
            assertEquals(offers[i], back[i]);   // 0..224 survives the unsigned-byte round trip
        }
    }

    public void testDecodeOffersNullEmpty() {
        assertNull(RenjuOpeningState.decodeOffers(null));
        assertEquals(0, RenjuOpeningState.decodeOffers(new byte[0]).length);
    }

    public void testNetSwappedAllPendingFalse() {
        RenjuOpeningState st = new RenjuOpeningState();
        assertTrue(!st.netSwapped());
        assertTrue(!RenjuOpeningState.netSwapped(st.encode()));
    }

    public void testNetSwappedEachSingleYesTrue() {
        for (int i = 0; i < 5; i++) {
            RenjuOpeningState st = new RenjuOpeningState();
            if (i == 0) st.swap1 = RenjuOpeningState.YES;
            if (i == 1) st.swap2 = RenjuOpeningState.YES;
            if (i == 2) st.swap3 = RenjuOpeningState.YES;
            if (i == 3) st.swap4 = RenjuOpeningState.YES;
            if (i == 4) st.swap5 = RenjuOpeningState.YES;
            assertTrue("digit " + i, st.netSwapped());
            assertTrue("digit " + i, RenjuOpeningState.netSwapped(st.encode()));
        }
    }

    public void testNetSwappedTwoYesCancel() {
        RenjuOpeningState st = new RenjuOpeningState();
        st.swap1 = RenjuOpeningState.YES;
        st.swap3 = RenjuOpeningState.YES;
        assertTrue(!st.netSwapped());
    }

    public void testNetSwappedThreeYesOdd() {
        RenjuOpeningState st = new RenjuOpeningState();
        st.swap1 = RenjuOpeningState.YES;
        st.swap2 = RenjuOpeningState.YES;
        st.swap5 = RenjuOpeningState.YES;
        assertTrue(st.netSwapped());
    }

    public void testNetSwappedBranchDigitIgnored() {
        RenjuOpeningState st = new RenjuOpeningState();
        st.branch = RenjuOpeningState.YES; // Branch B chosen — not a swap
        assertTrue(!st.netSwapped());
        st.swap2 = RenjuOpeningState.YES;
        assertTrue(st.netSwapped()); // branch still ignored on top of a swap
    }

    public void testNetSwappedNoDeclinesIgnored() {
        RenjuOpeningState st = new RenjuOpeningState();
        st.swap1 = RenjuOpeningState.NO;
        st.swap2 = RenjuOpeningState.NO;
        st.swap3 = RenjuOpeningState.NO;
        assertTrue(!st.netSwapped());
    }
}
