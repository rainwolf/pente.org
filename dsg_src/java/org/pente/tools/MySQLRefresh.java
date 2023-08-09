package org.pente.tools;

import java.io.*;
import java.text.*;
import java.util.*;

/**
 * Basically a script to restart the database and check the tables for
 * any corruption.  Started as a simple batch file but ms-dos can't redirect
 * stderr and myisamchk prints out all its important info to stderr.
 */
public class MySQLRefresh {

    private static final String lineSeparator = (String) System.getProperties().get("line.separator");

    public static void main(String args[]) throws IOException {

        if (args.length != 4) {
            System.err.println("Usage: " + MySQLRefresh.class.getName() + " <mysql home> <mysql user> <mysql passwd> <output file>");
            System.exit(-1);
        }

        String mysqlHome = args[0];
        String mysqlUser = args[1];
        String mysqlPasswd = args[2];
        String outputFile = args[3];

        FileOutputStream fileOutputStream = new FileOutputStream(outputFile, true);

        DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        StringBuffer buf = new StringBuffer();
        fileOutputStream.write(dateFormat.format(new Date()).getBytes());
        fileOutputStream.write((lineSeparator + lineSeparator).getBytes());
        fileOutputStream.flush();

        // should probably replace these hardcoded values
        fileOutputStream.write(("Shutting down mysqld server" + lineSeparator).getBytes());
        runProcess(true, fileOutputStream, new String[]{mysqlHome + "/bin/mysqladmin", "-u" + mysqlUser, "-p" + mysqlPasswd, "shutdown"});

        fileOutputStream.write(("Checking database tables [dsg]" + lineSeparator).getBytes());
        runProcess(true, fileOutputStream, new String[]{mysqlHome + "/bin/myisamchk", "--fast", "--silent", mysqlHome + "/data/dsg/*.MYI"});

        fileOutputStream.write(("Checking database tables [mysql]" + lineSeparator).getBytes());
        runProcess(true, fileOutputStream, new String[]{mysqlHome + "/bin/myisamchk", "--fast", "--silent", mysqlHome + "/data/mysql/*.MYI"});

        fileOutputStream.write(("Starting mysqld server" + lineSeparator).getBytes());
        runProcess(false, fileOutputStream, new String[]{mysqlHome + "/bin/mysqld-opt", "--log-bin=update-bin"});

        fileOutputStream.flush();
        fileOutputStream.close();
    }

    private static void runProcess(boolean waitFor, OutputStream outputStream, String cmd[]) throws IOException {

        Process process = Runtime.getRuntime().exec(cmd);
        if (waitFor) {
            try {
                process.waitFor();
            } catch (InterruptedException ex) {
            }
        }
        InputStream stdout = process.getInputStream();
        InputStream stderr = process.getErrorStream();

        // if seems possible that order of stdout and stderr could become messed up
        // since each is running in its own thread
        Thread stdoutThread = new MonitorInputStreamThread(stdout, outputStream);
        stdoutThread.start();

        Thread stderrThread = new MonitorInputStreamThread(stderr, outputStream);
        stderrThread.start();

        try {
            stdoutThread.join();
        } catch (InterruptedException ex) {
        }
        try {
            stderrThread.join();
        } catch (InterruptedException ex) {
        }

        if (waitFor) {
            outputStream.write(("Process exited with code: " + process.exitValue()).getBytes());
        }
        outputStream.write((lineSeparator + lineSeparator).getBytes());
    }

    private static class MonitorInputStreamThread extends Thread {

        private Reader reader;
        private Writer writer;

        public MonitorInputStreamThread(InputStream in, OutputStream out) {
            reader = new InputStreamReader(new BufferedInputStream(in));
            writer = new OutputStreamWriter(out);
            setDaemon(true);
        }

        public void run() {

            try {
                int c;
                while ((c = reader.read()) != -1) {

                    writer.write(c);
                    writer.flush();
                }
            } catch (IOException ioe) {
                ioe.printStackTrace(new PrintWriter(writer));
            }
        }
    }
}