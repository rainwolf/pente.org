package org.pente.tools;

import org.apache.tools.ant.*;
import org.apache.tools.ant.types.Commandline;
import org.apache.tools.ant.taskdefs.*;
import org.apache.tools.ant.taskdefs.ExecuteStreamHandler;

import java.io.*;


public class DiffTask extends Task {

    private File file1;
    private File file2;
    private File outputFile;

    public void setFile1(File file1) {
        this.file1 = file1;
    }

    public void setFile2(File file2) {
        this.file2 = file2;
    }

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    public void execute() throws BuildException {

        Commandline toExecute = new Commandline();
        toExecute.setExecutable("diff");

        toExecute.createArgument().setValue("-c");
        toExecute.createArgument().setFile(file1);
        toExecute.createArgument().setFile(file2);


        ExecuteStreamHandler handler = new FileStreamHandler(outputFile);

        Execute exe = new Execute(handler, null);
        exe.setCommandline(toExecute.getCommandline());

        System.out.println(toExecute.toString());
        System.out.println("Writing output to: " + outputFile);

        try {
            exe.execute();
        } catch (IOException e) {
            throw new BuildException(e);
        }
    }

    private class FileStreamHandler implements ExecuteStreamHandler {

        private FileOutputStream outputStream;
        private Thread thread;

        public FileStreamHandler(File outputFile) {
            try {
                this.outputStream = new FileOutputStream(outputFile);
            } catch (FileNotFoundException f) {

            }
        }

        public void setProcessInputStream(OutputStream os) {

        }

        public void setProcessErrorStream(InputStream is)
                throws IOException {

        }

        public void setProcessOutputStream(InputStream is)
                throws IOException {
            thread = new MonitorInputStreamThread(is, outputStream);
            thread.start();

            try {
                thread.join();
            } catch (InterruptedException ex) {
            }
        }

        public void start()
                throws IOException {
        }

        public void stop() {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                }
            }
            if (thread != null) {
                thread.interrupt();
            }
        }
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
