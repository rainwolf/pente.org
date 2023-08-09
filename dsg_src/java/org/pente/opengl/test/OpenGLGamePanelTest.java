package org.pente.opengl.test;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

import org.pente.gameServer.client.*;
import org.pente.gameServer.core.*;
import org.pente.opengl.OpenGLBoardPanel;

public class OpenGLGamePanelTest {

    private static final int INIT_HEIGHT = 800;
    private static final int INIT_WIDTH = 800;

    public static int player = 2;

    public static void main(String[] args) {

        final JFrame frame = new JFrame("OpenGL Game Panel");
        final PenteBoardComponent panel = new OpenGLBoardPanel();

        frame.getContentPane().add((Component) panel, BorderLayout.CENTER);

        JPanel c = new JPanel();
        JButton add = new JButton("Add Move");
        final JTextField move = new JTextField("180");
        add.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int m = Integer.parseInt(move.getText());
                int x = m % 19;
                int y = 18 - m / 19;
                player = 3 - player;
                GridPiece p = new SimpleGridPiece(x, y, player);
                panel.addPiece(p);
            }
        });

        JButton remove = new JButton("Remove Move");
        final JTextField move2 = new JTextField("180");
        remove.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int m = Integer.parseInt(move2.getText());
                int x = m % 19;
                int y = 18 - m / 19;
                GridPiece p = new SimpleGridPiece(x, y, 1);
                panel.removePiece(p);
            }
        });

        c.setLayout(new FlowLayout());
        c.add(add);
        c.add(move);
        c.add(remove);
        c.add(move2);
        frame.getContentPane().add(c, BorderLayout.SOUTH);

        frame.setSize(INIT_WIDTH, INIT_HEIGHT);
        frame.setVisible(true);

        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                frame.dispose();
                System.exit(0);
            }
        });

    }


}
