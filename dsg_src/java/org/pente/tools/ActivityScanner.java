package org.pente.tools;

import java.util.*;
import java.io.*;
import java.text.*;

import java.awt.*;
import java.awt.geom.Ellipse2D;

import org.jfree.data.xy.*;
import org.jfree.chart.*;
import org.jfree.chart.axis.*;
import org.jfree.chart.encoders.*;
import org.jfree.chart.renderer.xy.*;
import org.jfree.chart.plot.*;


public class ActivityScanner {

    private static final DateFormat dateFormat = new SimpleDateFormat(
            "yyyy-MM-dd");
    private static final DateFormat timeFormat = new SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss,S");

    private File inFiles[];
    private File outFile;
    private final long LOG_INTERVAL_MIN = 5;


    public static void main(String[] args) throws IOException, ParseException {

        File inFiles[] = new File[args.length - 1];
        for (int i = 0; i < inFiles.length; i++) {
            inFiles[i] = new File(args[i]);
        }
        File outFile = new File(args[args.length - 1]);

        ActivityScanner scanner = new ActivityScanner(inFiles, outFile);
        scanner.createChart();
    }


    public ActivityScanner(File inFiles[], File outFile) {

        this.inFiles = inFiles;
        this.outFile = outFile;

    }


    private int findStartCount() throws IOException {
        int startCount = 0;
        BufferedReader in = new BufferedReader(new InputStreamReader(
                new FileInputStream(inFiles[0])));

        String line = null;
        int countLoggedIn = 0;
        while ((line = in.readLine()) != null) {
            String type = line.substring(26, 30);
            if (type.equals("sess")) continue;
            if (type.charAt(0) > '0' && type.charAt(0) < '9') continue;

            if (type.equals("join")) {
                countLoggedIn++;
            } else if (type.equals("exit")) {
                countLoggedIn--;
                if (countLoggedIn < startCount) {
                    startCount = countLoggedIn;
                }
            }
        }

        in.close();

        return -startCount;
    }

    private XYDataset getDataSet() throws IOException, ParseException {

        int countLoggedIn = findStartCount();

        DefaultTableXYDataset dataset = new DefaultTableXYDataset();
        Date firstStartDate = dateFormat.parse(inFiles[0].getName().substring(13));

        for (int i = 0; i < 1; i++) {

            String date = inFiles[i].getName().substring(13);
            Date startDate = dateFormat.parse(date);
            XYSeries dataSeries = new XYSeries(date,
                    true, false);

            BufferedReader in = new BufferedReader(new InputStreamReader(
                    new FileInputStream(inFiles[i])));

            String line = null;
            while ((line = in.readLine()) != null) {
                String type = line.substring(26, 30);
                if (type.equals("sess")) continue;
                if (type.charAt(0) > '0' && type.charAt(0) < '9') continue;

                Date newDate = timeFormat.parse(line.substring(0, 23));
                long time = newDate.getTime() - startDate.getTime() + firstStartDate.getTime();
                //Date dd = new Date(time);
                //String ds = timeFormat.format(dd);

                if (type.equals("join")) {
                    countLoggedIn++;
                    dataSeries.add(time, countLoggedIn);
                } else if (type.equals("exit")) {
                    countLoggedIn--;
                    dataSeries.add(time, countLoggedIn);
                }
            }
            in.close();
            dataset.addSeries(dataSeries);
        }

        return dataset;
    }

    private Color colors[] = new Color[]{
            Color.red, Color.blue, Color.green, Color.yellow};

    private void createChart() {
        try {
            XYDataset dataset = getDataSet();

            //  Create the X-Axis
            DateAxis xAxis = new DateAxis(null);
            xAxis.setLowerMargin(0.0);
            xAxis.setUpperMargin(0.0);

            //  Create the X-Axis
            NumberAxis yAxis = new NumberAxis(null);
            yAxis.setAutoRangeIncludesZero(true);

            //  Create the renderer
//            XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
//            renderer.setLinesVisible(true);
            //XYLine3DRenderer renderer = new XYLine3DRenderer();
            StackedXYAreaRenderer renderer =
                    new StackedXYAreaRenderer(XYAreaRenderer.AREA_AND_SHAPES);
            for (int i = 0; i < inFiles.length; i++) {
                renderer.setSeriesPaint(i, colors[i]);
            }
            renderer.setShapePaint(Color.black);
            renderer.setShapeStroke(new BasicStroke(0.5f));
            renderer.setShape(new Ellipse2D.Double(0, 0, 0, 0));
            renderer.setOutline(true);

            //  Create the plot
            XYPlot plot = new XYPlot(dataset, xAxis, yAxis, renderer);
            //plot.setForegroundAlpha(0.65f);

            //  Reconfigure Y-Axis so the auto-range knows that the data is stacked
            yAxis.configure();
            xAxis.configure();

            //  Create the chart
            JFreeChart chart = new JFreeChart(null, JFreeChart.DEFAULT_TITLE_FONT, plot, true);
            chart.setBackgroundPaint(java.awt.Color.white);

            ImageEncoder encoder = new SunPNGEncoderAdapter();
            OutputStream out = new FileOutputStream(outFile);
            encoder.encode(chart.createBufferedImage(3000, 2000), out);
            out.close();

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
