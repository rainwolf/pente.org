package org.pente.gameServer.client.swing;

import java.awt.*;
import java.awt.image.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.*;
import javax.swing.*;

import org.pente.gameDatabase.swing.GameReviewBoard;

public class Test2 {

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

            final JFrame frame = new JFrame("Plunk");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            JButton b = new JButton("Test", getSuccessMarkerIcon(40));
            frame.getContentPane().add(b);

            frame.pack();
            frame.setSize(800, 800);
            frame.setVisible(true);
        });
    }
//	First, let's define a function that will return us an Icon:


    public static Icon getSuccessMarkerIcon(int dimension) {
        return new ImageIcon(drawBufferedPiece(dimension));
    }

//	Now, we define a function that creates a BufferedImage:


    public static BufferedImage drawBufferedPiece(int dimension) {

//	First, we create a new image and set it to anti-aliased mode:


        // new RGB image with transparency channel
        BufferedImage image = new BufferedImage(dimension, dimension,
                BufferedImage.TYPE_INT_ARGB);

        // create new graphics and set anti-aliasing hint
        Graphics2D graphics = (Graphics2D) image.getGraphics().create();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        graphics.rotate(-Math.PI / 6, dimension / 2, dimension / 2);

        // nice soft blue

        //Color topFillColor = new Color(185,221,236);
        //Color midFillColor = new Color(104,199,230);
        //Color botFillColor = new Color(40,182,226);
        //Color topShineColor = new Color(225,245,253);
        //Color bottomShineColor = new Color(225,245,253);

        // decent black

        Color topFillColor = new Color(32, 32, 32);
        Color midFillColor = new Color(16, 16, 16);
        Color botFillColor = Color.black;
        Color topShineColor = new Color(160, 160, 160);
        Color bottomShineColor = new Color(160, 160, 160);


//	      Color topFillColor = new Color(232,232,232);
//	      Color midFillColor = new Color(216,216,216);
//	      Color botFillColor = new Color(200,200,200);
//	      Color topShineColor = Color.white;
//	      Color bottomShineColor = Color.white;

        GradientPaint back = new GradientPaint(0, 0, topFillColor,
                dimension, dimension, botFillColor);
        graphics.setPaint(back);

        Shape s = new Ellipse2D.Double(0, 0, dimension - 1,
                dimension - 1);
        graphics.fill(s);


        graphics.clip(s);

        int width = dimension;
        int height = dimension;
        //int shineHeight = (int) (height / 1.8);
        int shineHeight = height;
        //int kernelSize = 12;
        int kernelSize = (int) Math.min(12, Math.pow(Math
                .min(width, height), 0.8) / 4);
        if (kernelSize == 0)
            kernelSize = 1;
        BufferedImage ghostContour = getBlankImage(width + 2 * kernelSize,
                height + 2 * kernelSize);
        Graphics2D ghostGraphics = (Graphics2D) ghostContour.getGraphics()
                .create();
        ghostGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        ghostGraphics.setStroke(new BasicStroke(2 * kernelSize));
        ghostGraphics.setColor(Color.black);
        ghostGraphics.translate(kernelSize, kernelSize);
        ghostGraphics.draw(s);


//	      graphics.drawImage(ghostContour, 0, 0, width - 1, shineHeight,
//	    	         kernelSize, kernelSize, kernelSize + width - 1, kernelSize
//	    	         + shineHeight, null);


        int kernelMatrixSize = (2 * kernelSize + 1) * (2 * kernelSize + 1);
        float[] kernelData = new float[kernelMatrixSize];
        for (int i = 0; i < kernelMatrixSize; i++)
            kernelData[i] = 1.0f / kernelMatrixSize;
        Kernel kernel = new Kernel(2 * kernelSize, 2 * kernelSize,
                kernelData);
        ConvolveOp convolve = new ConvolveOp(kernel);
        BufferedImage blurredGhostContour = getBlankImage(width + 2
                * kernelSize, height + 2 * kernelSize);
        convolve.filter(ghostContour, blurredGhostContour);


//	      graphics.drawImage(blurredGhostContour, 0, 0, width - 1, shineHeight,
//	    	         kernelSize, kernelSize, kernelSize + width - 1, kernelSize
//	    	         + shineHeight, null);


        BufferedImage reverseGhostContour = getBlankImage(width + 2
                * kernelSize, height + 2 * kernelSize);
        Graphics2D reverseGraphics = (Graphics2D) reverseGhostContour
                .getGraphics();
        Color bottomShineColorTransp = new Color(bottomShineColor.getRed(),
                bottomShineColor.getGreen(), bottomShineColor.getBlue(), 32);
        GradientPaint gradientShine = new GradientPaint(0, kernelSize,
                topShineColor, 0, kernelSize + shineHeight,
                bottomShineColorTransp);
        reverseGraphics.setPaint(gradientShine);
        reverseGraphics.fillRect(0, kernelSize, width + 2 * kernelSize,
                kernelSize + shineHeight);
        reverseGraphics.setComposite(AlphaComposite.DstOut);
        reverseGraphics.drawImage(blurredGhostContour, 0, 0, null);


        graphics.drawImage(reverseGhostContour, 0, 0, width - 1, shineHeight,
                kernelSize, kernelSize, kernelSize + width - 1, kernelSize
                        + shineHeight, null);


        BufferedImage overGhostContour = getBlankImage(width + 2
                * kernelSize, height + 2 * kernelSize);
        Graphics2D overGraphics = (Graphics2D) overGhostContour
                .getGraphics();
        overGraphics.setPaint(new GradientPaint(0, kernelSize,
                topFillColor, 0, kernelSize + height / 2, midFillColor));
        overGraphics.fillRect(kernelSize, kernelSize, kernelSize + width,
                kernelSize + shineHeight);
        overGraphics.setComposite(AlphaComposite.DstIn);
        overGraphics.drawImage(blurredGhostContour, 0, 0, null);


        graphics.drawImage(overGhostContour, 0, 0, width - 1, shineHeight,
                kernelSize, kernelSize, kernelSize + width - 1, kernelSize
                        + shineHeight, null);


        // dispose
        graphics.dispose();


        return image;
    }

    private static BufferedImage createShadowMask(BufferedImage image) {
        BufferedImage mask = new BufferedImage(image.getWidth(),
                image.getHeight(),
                BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2d = mask.createGraphics();
        g2d.drawImage(image, 0, 0, null);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_IN,
                0.5f));
        g2d.setColor(Color.black);
        g2d.fillRect(0, 0, image.getWidth(), image.getHeight());
        g2d.dispose();

        return mask;
    }

    private static ConvolveOp getLinearBlurOp(int size) {
        float[] data = new float[size * size];
        float value = 1.0f / (float) (size * size);
        for (int i = 0; i < data.length; i++) {
            data[i] = value;
        }
        return new ConvolveOp(new Kernel(size, size, data));
    }

    public static BufferedImage getBlankImage(int width, int height) {


        BufferedImage image = new BufferedImage(width, height,
                BufferedImage.TYPE_INT_ARGB);

        // get graphics and set hints
        Graphics2D graphics = (Graphics2D) image.getGraphics().create();

        graphics.setColor(new Color(0, 0, 0, 0));
        graphics.setComposite(AlphaComposite.Src);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();

        return image;
    }
}
