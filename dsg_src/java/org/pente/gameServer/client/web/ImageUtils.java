package org.pente.gameServer.client.web;

import java.awt.Transparency;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.*;
import java.io.*;
import java.util.*;

import javax.imageio.*;
import javax.imageio.stream.*;

public class ImageUtils {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws IOException {

		String type = args[0].substring(args[0].indexOf(".") + 1);
		String file = args[0].substring(0, args[0].indexOf("."));
		
		byte b[] = handleImage(
			type, new FileInputStream(args[0]));
		if (b == null) {
			System.out.println("image ok");
			return;
		}
		else {
		    File f = new File(file + "-sm." + type);
			FileOutputStream out = new FileOutputStream(f);
			out.write(b);
			out.close();
		}
		//BufferedImage bi = handleImage(
		//	type, new FileInputStream(args[0]));
//		if (bi == null) {
//			System.out.println("image ok");
//			return;
//		}
//		Iterator writers = ImageIO.getImageWritersByFormatName(type);
//		ImageWriter writer = (ImageWriter) writers.next();
//	    File f = new File(file + "-sm." + type);
//	    ImageOutputStream ios = ImageIO.createImageOutputStream(f);
////		if (bis.length == 1) {
//		    writer.setOutput(ios);
//		    writer.write(bi);
//		}
//		else {
//	        // prepare the sequence writer
//	        writer.setOutput(ios);
//	        writer.prepareWriteSequence(null);
//	        
//	        // write the sequence
//	        for (int i = 0; i < bis.length; i++) {
//	            IIOImage img = new IIOImage(bis[i], null, null);
//	            writer.writeToSequence(img, null);
//	            
//	        }
//	        // terminate the sequence writer
//	        writer.endWriteSequence();
//		}
	}
	
	public static int getWidth(String contentType, InputStream in) throws IOException {

		ImageReader reader = null;
		try {
			if (contentType.equals("pjpeg")) {
				contentType = "jpeg";
			}
			Iterator readers = ImageIO.getImageReadersByFormatName(contentType);
			reader = (ImageReader) readers.next();
			
			ImageInputStream iis = ImageIO.createImageInputStream(in);
			reader.setInput(iis, false);
	
			return reader.getWidth(0);
		}
		catch (IOException i) {
			//happens for some gifs
		}
		finally {
			if (reader != null) reader.dispose();
		}
		return 0;
	}
	
	public static byte[] handleImage(String contentType, InputStream in) 
		throws IOException {
		
		ImageReader reader = null;
		ImageWriter writer = null;
		
		try {
			Iterator readers = ImageIO.getImageReadersByFormatName(contentType);
			reader = (ImageReader) readers.next();
		
			ImageInputStream iis = ImageIO.createImageInputStream(in);
			reader.setInput(iis, false);
	
			int w =  reader.getWidth(0);
			int h = reader.getHeight(0);
	        int m = Math.max(w, h);
	        int max = 200;
	        if (m < max) {
	        	return null; // null means no changes needed
	        }
	        //System.out.println("w="+w+",h="+h);
			
	    	if (reader.getNumImages(true) > 1) {
	    		throw new IOException("Animated image is too big and can't be resized.");
	    	}
	    	
	//		BufferedImage bis[] = new BufferedImage[reader.getNumImages(true)];
	//		for (int i = 0; i < bis.length; i++) {
			BufferedImage bi = reader.read(0);
			
	
	    	double scale = ((double) max) / ((double) m);
	    	int newW = (int) (w * scale);
	    	int newH = (int) (h * scale);
		    System.out.println("new size = " + newW + "," + newH); 
		    bi = getScaledInstance(bi, newW, newH, RenderingHints.VALUE_INTERPOLATION_BILINEAR, true);
		    //}
		    //    bis[i] = bi;
			//}
			//return bis;
		    
		    Iterator writers = ImageIO.getImageWritersByFormatName(contentType);
			writer = (ImageWriter) writers.next();
		    ByteArrayOutputStream out = new ByteArrayOutputStream();
		    ImageOutputStream ios = ImageIO.createImageOutputStream(out);
			writer.setOutput(ios);
			writer.write(bi);
		    
		    return out.toByteArray();
		} finally {
			if (reader != null) reader.dispose();
			if (writer != null) writer.dispose();
		}
	}
	
    /**
     * Convenience method that returns a scaled instance of the
     * provided {@code BufferedImage}.
     *
     * @param img the original image to be scaled
     * @param targetWidth the desired width of the scaled instance,
     *    in pixels
     * @param targetHeight the desired height of the scaled instance,
     *    in pixels
     * @param hint one of the rendering hints that corresponds to
     *    {@code RenderingHints.KEY_INTERPOLATION} (e.g.
     *    {@code RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR},
     *    {@code RenderingHints.VALUE_INTERPOLATION_BILINEAR},
     *    {@code RenderingHints.VALUE_INTERPOLATION_BICUBIC})
     * @param higherQuality if true, this method will use a multi-step
     *    scaling technique that provides higher quality than the usual
     *    one-step technique (only useful in downscaling cases, where
     *    {@code targetWidth} or {@code targetHeight} is
     *    smaller than the original dimensions, and generally only when
     *    the {@code BILINEAR} hint is specified)
     * @return a scaled version of the original {@code BufferedImage}
     */
    public static BufferedImage getScaledInstance(BufferedImage img,
                                           int targetWidth,
                                           int targetHeight,
                                           Object hint,
                                           boolean higherQuality)
    {
        int type = (img.getTransparency() == Transparency.OPAQUE) ?
            BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        BufferedImage ret = (BufferedImage)img;
        int w, h;
        if (higherQuality) {
            // Use multi-step technique: start with original size, then
            // scale down in multiple passes with drawImage()
            // until the target size is reached
            w = img.getWidth();
            h = img.getHeight();
        } else {
            // Use one-step technique: scale directly from original
            // size to target size with a single drawImage() call
            w = targetWidth;
            h = targetHeight;
        }
        
        do {
            if (higherQuality && w > targetWidth) {
                w /= 2;
                if (w < targetWidth) {
                    w = targetWidth;
                }
            }

            if (higherQuality && h > targetHeight) {
                h /= 2;
                if (h < targetHeight) {
                    h = targetHeight;
                }
            }

    	    System.out.println("new size = " + w + "," + h); 
    	    
            BufferedImage tmp = new BufferedImage(w, h, type);
            Graphics2D g2 = tmp.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
            g2.drawImage(ret, 0, 0, w, h, null);
            g2.dispose();

            ret = tmp;
        } while (w != targetWidth || h != targetHeight);

        return ret;
    }

}
