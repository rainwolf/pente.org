package org.pente.turnBased.test;

import java.text.*;
import java.util.*;

import org.pente.turnBased.*;

public class TimeoutAdd {

    private static final DateFormat dateFormat = new SimpleDateFormat(
            "EEE, MMM dd, yyyy hh:mm:ss aa z Z");

    private static final long MILLIS_PER_DAY = 1000 * 60 * 60 * 24;
    private int daysPerMove = 4;
    private Date timeoutDate;

    public TimeoutAdd() {
        super();
    }

    public void addMove(int move) {
        updateTimeout();
    }

    public void updateTimeout() {


        Calendar now = Calendar.getInstance();

        long ml = MILLIS_PER_DAY * daysPerMove;
        long oml = ml;
        long t = System.currentTimeMillis();
        int wk1 = 7;//sat
        int wk2 = 1;//sun
        System.out.println("start, " + dateFormat.format(new Date(t)) + ", ml=" + ml);
        while (ml > 0) {
            now.setTimeInMillis(t);
            now.set(Calendar.HOUR_OF_DAY, 0);
            now.set(Calendar.MINUTE, 0);
            now.set(Calendar.SECOND, 0);
            long mctoday = t - now.getTimeInMillis();
            if (mctoday > MILLIS_PER_DAY) {
                mctoday = MILLIS_PER_DAY;//sanity check
            }
            int td = now.get(Calendar.DAY_OF_WEEK);

            if (td == wk1 || td == wk2) {
                t += MILLIS_PER_DAY - mctoday;
                if (ml != oml) {
                    ml += mctoday;
                }
                System.out.println(dateFormat.format(new Date(t)) + ", ml=" + ml);
            } else {
                long a = 0;
                if (ml == oml) {
                    a = MILLIS_PER_DAY - mctoday;
                } else if (ml > MILLIS_PER_DAY) {
                    a = MILLIS_PER_DAY;
                } else {
                    a = ml;
                }
                t += a;
                ml -= a;
                System.out.println(dateFormat.format(new Date(t)) + ", ml=" + ml + ",a=" + a);
            }
        }

        timeoutDate = new Date(t);
    }

    public long getTimeout() {
        return timeoutDate.getTime();
    }

    public String toString() {
        return "new timeout = " + dateFormat.format(timeoutDate) + " = " +
                timeoutDate.getTime();
    }

    /**
     * @param args
     */
    public static void main(String[] args) {

        TimeoutAdd t = new TimeoutAdd();
        t.addMove(180);
        System.out.println(t);
        //long time = System.currentTimeMillis();
        System.out.println(Utilities.getTimeLeft(t.getTimeout()));
    }

}
