package org.pente.opengl;

public class Piece {
	
	private static int id_nextval = 2;
	
	private int xCoord, yCoord;
	private double x, y;
	private int id;
	private int player;
	
    private float[] color;

	public Piece(double x, double y, int player, float color[]) {
		this(0, 0, x, y, player, color);
	}
	public Piece(int xCoord, int yCoord, double x, double y, int player, float color[]) {
		this.xCoord = xCoord;
		this.yCoord = yCoord;
		this.x = x;
		this.y = y;
		this.player = player;
		this.color = color;
		id = id_nextval++;
	}

	public int getId() {
		return id;
	}

	public int getXCoord() {
		return xCoord;
	}
	public void setXCoord(int coord) {
		xCoord = coord;
	}
	public int getYCoord() {
		return yCoord;
	}
	public void setYCoord(int coord) {
		yCoord = coord;
	}
	
	public double getX() {
		return x;
	}
	public void setX(double x) {
		this.x = x;
	}

	public double getY() {
		return y;
	}
	public void setY(double y) {
		this.y = y;
	}
	
	public int getPlayer() {
		return player;
	}

	public float[] getColor() {
		return color;
	}
	
	public String toString() {
		return getId() + ": " + x + "," + y;
	}
}
