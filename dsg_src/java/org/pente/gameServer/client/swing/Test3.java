package org.pente.gameServer.client.swing;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputMethodEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.*;
import java.util.Iterator;

import javax.swing.*;

import org.pente.gameDatabase.GameStorerSearchResponseMoveData;
import org.pente.gameDatabase.swing.GameReviewBoard;
import org.pente.gameServer.client.GameOptions;
import org.pente.gameServer.client.SimpleGameOptions;
import org.pente.gameServer.core.AlphaNumericGridCoordinates;
import org.pente.gameServer.core.SimpleGridPiece;

public class Test3 {

    /**
     * @param args
     */
    public static void main(String[] args) {
        // TODO Auto-generated method stub

        try {
            UIManager.setLookAndFeel(
                    UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
        }

        javax.swing.SwingUtilities.invokeLater(() -> {
            JFrame.setDefaultLookAndFeelDecorated(true);

            final JFrame frame = new JFrame("Test colors");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

            final JTextField t = new JTextField("1.0");
            frame.getContentPane().add(t, BorderLayout.CENTER);

            final JButton b = new JButton("Test", getIcon(20, 20, 1));
            b.addActionListener(arg0 -> b.setIcon(getIcon(20, 20, Float.parseFloat(t.getText()))));
            frame.getContentPane().add(b, BorderLayout.NORTH);


            PenteBoardLW lw = new PenteBoardLW();
            lw.gridCoordinatesChanged(new AlphaNumericGridCoordinates(19, 19));
            final GameOptions gameOptions = new SimpleGameOptions(3);
            gameOptions.setPlayerColor(GameOptions.WHITE, 1);
            gameOptions.setPlayerColor(GameOptions.BLACK, 2);
            gameOptions.setPlayerColor(GameOptions.GREEN, 3); // for search moves search moves
            gameOptions.setDraw3DPieces(true);
            gameOptions.setPlaySound(true);
            gameOptions.setShowLastMove(true);
            lw.gameOptionsChanged(gameOptions);
            SimpleGridPiece p = new SimpleGridPiece(9, 9, 3);
            p.setColor(getColor(1.0));
            frame.getContentPane().add(lw, BorderLayout.SOUTH);

            frame.pack();
            frame.setSize(600, 600);
            frame.setVisible(true);

            lw.addPiece(p);
        });
    }
//	First, let's define a function that will return us an Icon:


    public static Icon getIcon(int x, int y, double percent) {
        return new ImageIcon(getSuccessMarker(x, y, percent));
    }

//	Now, we define a function that creates a BufferedImage:


    public static BufferedImage getSuccessMarker(int x, int y, double percent) {

//	First, we create a new image and set it to anti-aliased mode:


        // new RGB image with transparency channel
        BufferedImage image = new BufferedImage(x, y,
                BufferedImage.TYPE_INT_ARGB);

        // create new graphics and set anti-aliasing hint
        Graphics2D graphics = (Graphics2D) image.getGraphics().create();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

	      /*
	      GradientPaint p = new GradientPaint(x/2, 0, Color.red,
	    		  x/2, y/3, Color.yellow);
	      graphics.setPaint(p);
	      graphics.fillRect(0, 0, x, y/3);
	      p = new GradientPaint(x/2, y/3, Color.yellow,
	    		  x/2, 2*y/3, Color.green);
	      graphics.setPaint(p);
	      graphics.fillRect(0, y/3, x, 2*y/3);
	      p = new GradientPaint(x/2, 2*y/3, Color.green,
	    		  x/2, y, Color.blue);
	      graphics.setPaint(p);
	      graphics.fillRect(0, 2*y/3, x, y);
		  */
        //TODO redo this algorithm it is misleading


        //1.0=red
        //.8=light red
        //.6=light orange
        //.4=lighter orange
        //.2=yellow

        //existing straight mapping
        int green = 237 - (int) (196 * percent);
        System.out.println("oldp=" + percent);
        if (percent > .6f) {
            percent = .8f + ((percent + .4f - 1) / 2);
        } else if (percent > .5f) {
            percent = .6f + ((percent + .5f - 1) * 2);
        } else if (percent > .4f) {
            percent = .4f + (percent + .6f - 1);
        } else if (percent > .3f) {
            percent = .2f + (percent + .7f - 1);
        } else {
            percent = 0 + percent / 2;
        }
        green = 237 - (int) (196 * percent);
        System.out.println("newp=" + percent);

        graphics.setColor(new Color(255, green, 41));
        graphics.fillOval(0, 0, x, y);

        // dispose
        graphics.dispose();
        return image;
    }

    private static Color getColor(double percent) {
        System.out.println("oldp=" + percent);
        if (percent > .6f) {
            percent = .8f + ((percent + .4f - 1) / 2);
        } else if (percent > .5f) {
            percent = .6f + ((percent + .5f - 1) * 2);
        } else if (percent > .4f) {
            percent = .4f + (percent + .6f - 1);
        } else if (percent > .3f) {
            percent = .2f + (percent + .7f - 1);
        } else {
            percent = 0 + percent / 2;
        }
        int green = 237 - (int) (196 * percent);
        System.out.println("newp=" + percent);

        return new Color(255, green, 41);
    }

}
