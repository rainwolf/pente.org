package org.pente.subscriptions.test;

import junit.framework.TestCase;

import org.pente.subscriptions.IOSReceiptServlet;
import org.pente.subscriptions.IOSReceiptServlet.ReceiptClass;
import org.pente.subscriptions.IOSReceiptServlet.ReceiptOutcome;

/**
 * Mapping of Apple verifyReceipt status codes to outcomes (IOSReceiptServlet.statusOutcome)
 * and the retry reply contract shipped clients rely on.
 *
 * Run: ant test-one -Dtest=org.pente.subscriptions.test.IOSReceiptStatusTest
 */
public class IOSReceiptStatusTest extends TestCase {

    public IOSReceiptStatusTest(String name) {
        super(name);
    }

    public void testZeroIsValid() {
        assertEquals(ReceiptOutcome.VALID, IOSReceiptServlet.statusOutcome(0));
    }

    public void testReceiptProblemsAreInvalid() {
        int[] codes = {21000, 21002, 21003, 21006, 21008, 21010};
        for (int code : codes) {
            assertEquals("status " + code, ReceiptOutcome.INVALID, IOSReceiptServlet.statusOutcome(code));
        }
    }

    public void testServerAndConfigProblemsAreTransient() {
        int[] codes = {21004, 21005, 21009, 21100, 21150, 21199};
        for (int code : codes) {
            assertEquals("status " + code, ReceiptOutcome.TRANSIENT, IOSReceiptServlet.statusOutcome(code));
        }
    }

    public void testUnknownStatusIsTransient() {
        int[] codes = {1, -1, 21001, 21011, 21099, 21200, 99999};
        for (int code : codes) {
            assertEquals("status " + code, ReceiptOutcome.TRANSIENT, IOSReceiptServlet.statusOutcome(code));
        }
    }

    /** clients clear their pending receipt on "success" or "invalid receipt"; a retry must do neither */
    public void testRetryReplyKeepsClientRetrying() {
        String reply = IOSReceiptServlet.RETRY_REPLY;
        assertTrue(!reply.contains("success"));
        assertTrue(!reply.contains("invalid receipt"));
        for (ReceiptClass c : ReceiptClass.values()) {
            assertTrue(!reply.equals(c.getReply()));
        }
    }
}
