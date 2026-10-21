package org.pente.gameServer.tourney.test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import junit.framework.TestCase;

import org.pente.gameServer.tourney.Tourney;
import org.pente.gameServer.tourney.TourneyWithdraw;
import org.pente.gameServer.tourney.TourneyWithdraw.Result;

public class TourneyWithdrawTest extends TestCase {

    private static final long DAY = 24L * 60 * 60 * 1000;

    public TourneyWithdrawTest(String name) {
        super(name);
    }

    private static Tourney tourney(int eid, String name, Date signupEnd) {
        Tourney t = new Tourney();
        t.setEventID(eid);
        t.setName(name);
        t.setSignupEndDate(signupEnd);
        return t;
    }

    public void testOpenWhenUpcomingAndFutureEnd() {
        Date now = new Date();
        Tourney t = tourney(1, "Swiss Cup", new Date(now.getTime() + DAY));
        List<Tourney> upcoming = new ArrayList<Tourney>();
        upcoming.add(t);
        assertTrue(TourneyWithdraw.isRegistrationOpen(t, upcoming, now));
    }

    public void testCancelledTourneyIsClosed() {
        Date now = new Date();
        Tourney t = tourney(2, "Cancelled Cup", new Date(now.getTime() + DAY));
        List<Tourney> upcoming = new ArrayList<Tourney>();
        assertTrue(!TourneyWithdraw.isRegistrationOpen(t, upcoming, now));
    }

    public void testPastEndIsClosed() {
        Date now = new Date();
        Tourney t = tourney(3, "Old Cup", new Date(now.getTime() - DAY));
        List<Tourney> upcoming = new ArrayList<Tourney>();
        upcoming.add(t);
        assertTrue(!TourneyWithdraw.isRegistrationOpen(t, upcoming, now));
    }

    public void testNullTourneyIsClosed() {
        Date now = new Date();
        List<Tourney> upcoming = new ArrayList<Tourney>();
        assertTrue(!TourneyWithdraw.isRegistrationOpen(null, upcoming, now));

        Tourney noEnd = tourney(4, "No End Cup", null);
        upcoming.add(noEnd);
        assertTrue(!TourneyWithdraw.isRegistrationOpen(noEnd, upcoming, now));
    }

    public void testCheckOrder() {
        Tourney t = tourney(5, "Order Cup", new Date());
        assertEquals(Result.NOT_LOGGED_IN, TourneyWithdraw.check(false, null, false, false));
        assertEquals(Result.NO_TOURNEY, TourneyWithdraw.check(true, null, false, false));
        assertEquals(Result.REGISTRATION_CLOSED, TourneyWithdraw.check(true, t, false, false));
        assertEquals(Result.NOT_REGISTERED, TourneyWithdraw.check(true, t, true, false));
        assertEquals(Result.OK, TourneyWithdraw.check(true, t, true, true));
    }

    public void testMessages() {
        assertEquals("Registration for Swiss Cup closed, so you can no longer withdraw.",
                TourneyWithdraw.message(Result.REGISTRATION_CLOSED, "Swiss Cup"));
        assertEquals("You withdrawn from Swiss Cup. You can sign up again while registration open.",
                TourneyWithdraw.message(Result.OK, "Swiss Cup"));
        assertEquals("You not signed up for Swiss Cup.",
                TourneyWithdraw.message(Result.NOT_REGISTERED, "Swiss Cup"));
    }
}
