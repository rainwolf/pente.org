package org.pente.tools;

import java.io.*;
import java.sql.*;

import java.awt.*;
import java.awt.geom.Ellipse2D;

import org.apache.log4j.BasicConfigurator;
import org.jfree.data.xy.*;
import org.jfree.chart.*;
import org.jfree.chart.axis.*;
import org.jfree.chart.encoders.*;
import org.jfree.chart.renderer.xy.*;
import org.jfree.chart.plot.*;
import org.pente.database.DBHandler;
import org.pente.database.MySQLDBHandler;
import org.pente.game.GridStateFactory;


public class RatingsGrapher {


	public static void main(String[] args) throws Throwable {
		BasicConfigurator.configure();

        DBHandler dbHandler = new MySQLDBHandler(
            args[0], args[1], args[2], args[3]);

        long pid = Long.parseLong(args[4]);
        int game = Integer.parseInt(args[5]);
        try {
            OutputStream out = new FileOutputStream("/ratings.png");
        	new RatingsGrapher(dbHandler, pid, game).generateGraph(out);
	        out.close();
	        
        } finally {
        	dbHandler.destroy();
        }
	}

	private DBHandler dbHandler;
	private long pid;
	private int game;

	public RatingsGrapher(DBHandler dbHandler, long pid, int game) {
		this.dbHandler = dbHandler;
		this.pid = pid;
		this.game = game;
	}
	
	public void generateGraph(OutputStream out) {
        try {
	        XYDataset dataset = getDataSet();
	
	        //  Create the X-Axis
	        DateAxis xAxis = new DateAxis(null);
	        xAxis.setLowerMargin(0.0);
	        xAxis.setUpperMargin(0.0);
	
	        
	        //  Create the X-Axis
	        NumberAxis yAxis = new NumberAxis(null);
	        yAxis.setAutoRangeIncludesZero(false);
	
	        
	        //  Create the renderer
	//        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
	//        renderer.setLinesVisible(true);
	        XYLine3DRenderer renderer = new XYLine3DRenderer();
	        //StackedXYAreaRenderer renderer =
	        //   new StackedXYAreaRenderer(XYAreaRenderer.AREA_AND_SHAPES);
	        renderer.setXOffset(0);
	        renderer.setYOffset(0);
	        renderer.setSeriesPaint(0, Color.blue);
	        renderer.setSeriesPaint(1, Color.red);
	        //renderer.setSeriesVisible(1, false);
	        //renderer.setSeriesStroke(0, new BasicStroke(1f));
	
	        //renderer.setShapePaint(Color.black);
	        //renderer.setShapeStroke(new BasicStroke(0.5f));
	        renderer.setShape(new Ellipse2D.Double(0, 0, 0, 0));
	        //renderer.setOutline(true);
	
	        //  Create the plot
	        XYPlot plot = new XYPlot(dataset, xAxis, yAxis, renderer);
	        plot.setRangeGridlinesVisible(false);
	
	        //plot.setForegroundAlpha(0.65f);
	
	        //  Reconfigure Y-Axis so the auto-range knows that the data is stacked
	        yAxis.configure();
	        xAxis.configure();
	
	        //  Create the chart
	        JFreeChart chart = new JFreeChart(null, JFreeChart.DEFAULT_TITLE_FONT, plot, true);
	        chart.setBackgroundPaint(java.awt.Color.white);
	
	        ImageEncoder encoder = new SunPNGEncoderAdapter();
	        encoder.encode(chart.createBufferedImage(600, 300), out);

	
	    } catch (Throwable t) {
	        t.printStackTrace();
	    }
	}
    private XYDataset getDataSet() throws SQLException  {

        DefaultTableXYDataset dataset = new DefaultTableXYDataset();
        XYSeries ratingSeries = new XYSeries("Rating",
            true, false);
        XYSeries oppRatingSeries = new XYSeries("Opponent Rating",
        	true, false);
        
        if (GridStateFactory.isTurnbasedGame(game)) {
        	
        }
        Connection con = null;
        ResultSet results = null;
        PreparedStatement stmt = null;
        try {
        	con = dbHandler.getConnection();
        	stmt = con.prepareStatement(
        		"select player1_rating as me_rating, player2_rating as opp_rating, play_date " +
        		"from pente_game g1 " +
        		"where player1_pid = ? " +
        		"and rated = 'Y' " +
        		"and game = ? " +
        		"and player1_rating > 0 " +
        		((GridStateFactory.isTurnbasedGame(game)) ? 
        				"and gid > 50000000000000 " : // include turn-based games only 
        				"and gid < 50000000000000 ") + //exclude turn-based games
        		"union " +
        		"select player2_rating as me_rating, player1_rating as opp_rating, play_date " +
        		"from pente_game " +
        		"where player2_pid = ? " +
        		"and rated = 'Y' " +
        		"and game = ? " +
        		"and player2_rating > 0 " +
        		((GridStateFactory.isTurnbasedGame(game)) ? 
        				"and gid > 50000000000000 " : // include turn-based games only 
        				"and gid < 50000000000000 ") + //exclude turn-based games
        		"order by play_date");
        	stmt.setLong(1, pid);
        	stmt.setInt(2, GridStateFactory.isTurnbasedGame(game) ? 
        		GridStateFactory.getNormalGameFromTurnbased(game) : game);
        	stmt.setLong(3, pid);
        	stmt.setInt(4, GridStateFactory.isTurnbasedGame(game) ? 
            	GridStateFactory.getNormalGameFromTurnbased(game) : game);
        	
        	results = stmt.executeQuery();
        	long lastTime = -1;
        	while (results.next()) {
        		double rating = results.getDouble(1);
        		//double oppRating = results.getDouble(2);
        		long time = results.getTimestamp(3).getTime();
        		while (time <= lastTime) {	// games sometimes end at same time
        			time++;
        		}
        		lastTime = time;
        		ratingSeries.add(time, rating);
        		//oppRatingSeries.add(time, oppRating);
        	}
        	dataset.addSeries(ratingSeries);
        	//dataset.addSeries(oppRatingSeries);
        
        } finally {
        	if (results != null) results.close();
        	if (stmt != null) stmt.close();
        	if (con != null) dbHandler.freeConnection(con);
        }
        return dataset;
    }
}
