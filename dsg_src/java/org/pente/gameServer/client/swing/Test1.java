package org.pente.gameServer.client.swing;

import java.awt.*;
import java.awt.image.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.*;
import javax.swing.*;

import org.pente.gameDatabase.swing.GameReviewBoard;

public class Test1 {

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

        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                JFrame.setDefaultLookAndFeelDecorated(true);

                final JFrame frame = new JFrame("Plunk");

                JButton b = new JButton("Test", getSuccessMarkerIcon(150));
                frame.getContentPane().add(b);

                frame.pack();
                frame.setSize(800, 800);
                frame.setVisible(true);
            }
        });
    }
//	First, let's define a function that will return us an Icon:


    public static Icon getSuccessMarkerIcon(int dimension) {
        return new ImageIcon(getSuccessMarker(dimension));
    }

//	Now, we define a function that creates a BufferedImage:


    public static BufferedImage getSuccessMarker(int dimension) {

//	First, we create a new image and set it to anti-aliased mode:


        // new RGB image with transparency channel
        BufferedImage image = new BufferedImage(dimension, dimension,
                BufferedImage.TYPE_INT_ARGB);

        // create new graphics and set anti-aliasing hint
        Graphics2D graphics = (Graphics2D) image.getGraphics().create();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);


//	Fill the background:


        // green background fill
        //graphics.setColor(new Color(0, 196, 0));
        graphics.setColor(Color.red);
        graphics.fillOval(0, 0, dimension - 1, dimension - 1);

//	Create a white spot in the top-left corner (to simulate 3D shining effect) - note that we set clipping area to the icon circle (so that all the rest will remain transparent when our icon will be shown on non-white background):


        // create spot in the upper-left corner using temporary graphics
        // with clip set to the icon outline

        GradientPaint spot = new GradientPaint(0, 0, new Color(255, 255, 255,
                120), dimension, dimension, new Color(255, 255, 255, 0));
        Graphics2D tempGraphics = (Graphics2D) graphics.create();
        tempGraphics.setPaint(spot);
        tempGraphics.setClip(new Ellipse2D.Double(0, 0, dimension - 1,
                dimension - 1));
        tempGraphics.fillRect(0, 0, dimension, dimension);
        tempGraphics.dispose();

//	Draw the outline (must be done after the white gradient so the outline is not affected by it):


        // draw outline of the icon
        graphics.setColor(new Color(0, 0, 0, 128));
        graphics.drawOval(0, 0, dimension - 1, dimension - 1);

//	Compute the stroke width for the V sign. This sign is created using the same path with different strokes, one for the outer rim (wider), and one for the inner filling (narrower).


        // draw the V sign
        float dimOuter = (float) (0.5f * Math.pow(dimension, 0.75));
        float dimInner = (float) (0.28f * Math.pow(dimension, 0.75));

//	Create a GeneralPath for the V sign


        // create the path itself
        GeneralPath gp = new GeneralPath();
        gp.moveTo(0.25f * dimension, 0.45f * dimension);
        gp.lineTo(0.45f * dimension, 0.65f * dimension);
        gp.lineTo(0.85f * dimension, 0.12f * dimension);

//	Draw the path twice


        // draw blackish outline
        graphics.setStroke(new BasicStroke(dimOuter, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        graphics.setColor(new Color(0, 0, 0, 196));
        graphics.draw(gp);
        // draw white inside
        graphics.setStroke(new BasicStroke(dimInner, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
        graphics.setColor(Color.white);
        graphics.draw(gp);

//	Dispose of the temp graphics and return the image


        // dispose
        graphics.dispose();
        return image;
    }

}
