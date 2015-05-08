package org.pente.tools;

import java.awt.*;
import java.awt.color.*;

public class ColorPicker {
    public static void main(String[] args) {
        //System.out.println(java.awt.Color.black.getRGB());
        //System.out.println(new Color(41, 102, 255).getRGB());
        
        
//        Color c = Color.YELLOW;
//        int r = 255;//c.getRed();
//        int g = 255;//c.getGreen();
//        int b = 204;//c.getBlue();
//        int min = Math.min(r, Math.min(g, b));
//        int max = Math.max(r, Math.max(g, b));
//        int l = (min + max) / 2;
//        System.out.println("l = " + l);

    	
    	
    	//        themin = MIN(c1.r,MIN(c1.g,c1.b));
//        themax = MAX(c1.r,MAX(c1.g,c1.b));
//        delta = themax - themin;
//        c2.l = (themin + themax) / 2;
        
//        ColorSpace hsl = ColorSpace.getInstance(ColorSpace.TYPE_HLS);
//        
//        float vals[] = hsl.fromRGB(new float[] { 0, 0, 0 });
//        
//        System.out.print(vals[0]);
//        System.out.print(vals[1]);
//        System.out.print(vals[2]);
        
        //Color backGroundColor = new Color(255, 222, 165);
        Color backGroundColor = new Color(186, 253, 163);
        float hsb[] = Color.RGBtoHSB(backGroundColor.getRed(),
            backGroundColor.getGreen(), backGroundColor.getBlue(), null);
        System.out.println("hsb=" + hsb[0] +","+hsb[1]+","+hsb[2]);
        
        hsb[2]-=0.1;

        Color c = Color.getHSBColor(hsb[0], hsb[1], hsb[2]);
        System.out.println("game name=" + c.getRed() + ","+c.getGreen() + ","+
        	c.getBlue());
        
        Color c2 = backGroundColor.darker();
        System.out.println("game name=" + c2.getRed() + ","+c2.getGreen() + ","+
            	c2.getBlue());
    }
}
