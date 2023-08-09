package org.pente.gameServer.client.awt;

import java.awt.*;
import java.net.*;

public class ImageCanvas extends Canvas {

    //private byte imageBytes[];
    private Image image;
    private boolean notified = false;

    private String playerName;
    private String host;
    private boolean donor;

    private Dimension size = new Dimension(50, 50);

    public ImageCanvas() {
    }

    public ImageCanvas(String playerName, String host) {
        this.playerName = playerName;
        this.host = host;
    }

    public ImageCanvas(Image image) {
        this.image = image;
    }

    public void updateImage(boolean donor) {
        this.donor = donor;
        if (!donor) return;
        if (notified) {
            makeImage();
        }
        repaint();
    }


    public void addNotify() {
        super.addNotify();
        makeImage();

        notified = true;
    }

    private void makeImage() {

        if (!donor) return;

        try {
            URL imageUrl = new URL("https", host, "/gameServer/avatar?name=" + playerName);

            image = Toolkit.getDefaultToolkit().getImage(imageUrl);
            //image = Toolkit.getDefaultToolkit().createImage(imageBytes);
            MediaTracker mediaTracker = new MediaTracker(this);
            mediaTracker.addImage(image, 0);
            try {
                mediaTracker.waitForID(0);
            } catch (InterruptedException ie) {
            }
        } catch (MalformedURLException mue) {
            mue.printStackTrace();
        }
    }

    public void destroy() {
        if (image != null) {
            image.flush();
            image = null;
        }
    }

    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    public Dimension getPreferredSize() {
        if (image == null) return size;
        return new Dimension(image.getWidth(null) + 12,
                image.getHeight(null) + 12);
    }

    public void update(Graphics g) {
        paint(g);
    }

    public void paint(Graphics g) {
        if (image == null) {
            super.paint(g);
        } else {
            Dimension d = getMinimumSize();
            g.setColor(Color.black);
            g.fillRect(3, 3, d.width - 6, d.height - 6);
            g.drawImage(image, 6, 6, this);
        }
    }
}
