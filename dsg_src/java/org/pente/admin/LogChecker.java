package org.pente.admin;

import java.io.*;
import java.util.*;

import java.text.*;

import org.apache.log4j.*;

public class LogChecker {

    private File logDir;

    private Date minDate;
    private Date maxDate;


    private static final DateFormat dateFormat =
            new SimpleDateFormat("yyyy-MM-dd");

    public LogChecker(File logDir) {
        this.logDir = logDir;
        scanDir();
    }

    private void scanDir() {

        File files[] = logDir.listFiles((dir, name) -> name.startsWith("activity.log"));
        for (int i = 0; i < files.length; i++) {
            try {
                Date d = null;
                String fn = files[i].getName();
                if (fn.equals("activity.log")) {
                    d = new Date();//today
                } else {
                    d = dateFormat.parse(fn.substring(13));
                }

                if (minDate == null || d.before(minDate)) {
                    minDate = d;
                }
                if (maxDate == null || d.after(maxDate)) {
                    maxDate = d;
                }
            } catch (ParseException pe) {
                pe.printStackTrace();
            }
        }
    }

    private File getFile(String date) {
        if (dateFormat.format(new Date()).equals(date)) {
            return new File(logDir, "activity.log");
        } else {
            return new File(logDir, "activity.log." + date);
        }
    }

    public List<LogData> scanFile(String date) throws IOException {
        List<LogData> events = new ArrayList<LogData>();
        int num = 0;
        BufferedReader in = new BufferedReader(
                new FileReader(getFile(date)));
        String line = null;
        while ((line = in.readLine()) != null) {
            String susp = line.substring(26, 29);
            if (susp.equals("2: ") || susp.equals("5: ")) {
                events.add(new LogData(num, line));
            }
            num++;
        }
        return events;
    }

    public int getFileLength(String date) throws IOException {
        int len = 0;
        BufferedReader in = new BufferedReader(
                new FileReader(getFile(date)));
        while (in.readLine() != null) {
            len++;
        }
        return len;
    }

    public String[] getFileSection(String date, int lineStart, int lineEnd)
            throws IOException {

        String lines[] = new String[lineEnd - lineStart];
        int i = 0;
        int len = 0;
        BufferedReader in = new BufferedReader(
                new FileReader(getFile(date)));
        String line = null;
        while ((line = in.readLine()) != null) {
            if (len >= lineEnd) break;
            if (len >= lineStart) {
                lines[i++] = line;
            }
            len++;
        }
        return lines;
    }

    public Date getMinDate() {
        return minDate;
    }

    public Date getMaxDate() {
        return maxDate;
    }

//  not going to do this, just have to enter in date manually
//	public int[] getYearRange() {
//		Calendar c1 = Calendar.getInstance();
//		c1.setTime(minDate);
//		Calendar c2 = Calendar.getInstance();
//		c2.setTime(maxDate);
//		
//		int sy = c1.get(Calendar.YEAR);
//		int ey = c2.get(Calendar.YEAR);
//		int r[] = new int[ey - sy + 1];
//		for (int i = 0; i + sy <= ey; i++) {
//			r[i] = i + sy;
//		}
//		return r;
//	}


    public static void main(String args[]) throws IOException {
        LogChecker l = new LogChecker(new File(args[0]));
        l.scanFile("2005-12-02");
    }
}
