package org.pente.gameServer.client.awt.test;

import java.awt.*;
import java.awt.event.*;



public class MainRoomLayoutTest {

	public static void main(String args[]) {
		
		final Frame f = new Frame("MainRoomLayoutTest");
		
		Panel p = new Panel();
        GridBagLayout gridbag = new GridBagLayout();
        GridBagConstraints constraints = new GridBagConstraints();
        p.setLayout(gridbag);

        constraints.insets = new Insets(2, 2, 2, 2);

        constraints.gridx = 1;
        constraints.gridy = 1;
        constraints.gridheight = 1;
        constraints.gridwidth = 1;
        constraints.weightx = 1;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.BOTH;
        Panel p1 = new Panel();
        p1.setBackground(Color.yellow);
        gridbag.setConstraints(p1, constraints);
        p.add(p1);
        
        constraints.gridx = 1;
        constraints.gridy = 2;
        constraints.gridheight = 1;
        constraints.gridwidth = 1;
        //constraints.weightx = 90;
        //constraints.weighty = 1;
        constraints.fill = GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.WEST;
        Panel p2 = new Panel();
        p2.setBackground(Color.gray);
        gridbag.setConstraints(p2, constraints);
        p.add(p2);

//        constraints.gridy = 3;
//        constraints.gridx = 1;
//        constraints.gridheight = 1;
//        constraints.gridwidth = 1;
//        constraints.weightx = 90;
//        constraints.weighty = 24;
//        constraints.fill = GridBagConstraints.BOTH;
//        Panel p3 = new Panel();
//        p3.setBackground(Color.red);
//        gridbag.setConstraints(p3, constraints);
//        p.add(p3);
//        
//        constraints.gridy = 4;
//        constraints.gridx = 1;
//        constraints.gridheight = 1;
//        constraints.gridwidth = 1;
//        constraints.weightx = 90;
//        constraints.weighty = 24;
//        constraints.fill = GridBagConstraints.BOTH;
//        Panel p6 = new Panel();
//        p6.setBackground(Color.green);
//        gridbag.setConstraints(p6, constraints);
//        p.add(p6);
        
        constraints.gridy = 3;
        constraints.gridx = 1;
        constraints.gridheight = 2;
        constraints.gridwidth = 1;
        //constraints.weightx = 90;
        //constraints.weighty = 48;
        constraints.fill = GridBagConstraints.BOTH;
        Panel p6 = new Panel();
        p6.setBackground(Color.green);
        gridbag.setConstraints(p6, constraints);
        p.add(p6);
        
        constraints.gridx = 2;
        constraints.gridy = 1;
        constraints.gridheight = 4;
        constraints.gridwidth = 1;
        //constraints.weightx = 10;
        //constraints.weighty = 24+1+48;
        constraints.fill = GridBagConstraints.BOTH;
        Panel p4 = new Panel();
        p4.setBackground(Color.blue);
        p4.setLayout(new BorderLayout());
        Panel p10 = new Panel();
        p10.setBackground(Color.ORANGE);
        Button b = new Button("Hello");
        p4.add("Center", p10);
        p4.add("South", b);
        
        gridbag.setConstraints(p4, constraints);
        p.add(p4);

        
        f.add(p);
		f.setSize(300, 300);
		f.setVisible(true);
		
		f.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				f.dispose();
			}
		});
    }
}

