package org.pente.gameDatabase.swing;

import java.awt.Component;

import javax.swing.*;

import org.pente.gameServer.core.SimpleDSGPlayerGameData;

import javax.swing.table.DefaultTableCellRenderer;

public class GameTableRenderer extends DefaultTableCellRenderer {

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, 
		boolean isSelected, boolean hasFocus, int row, int col) {

		ImageIcon i = new ImageIcon(GameReviewBoard.class.getResource("images/" +
			SimpleDSGPlayerGameData.getRatingsGifRatingOnly(((Integer)value).intValue())));

		setIcon(i);

		return super.getTableCellRendererComponent(
			table, value, isSelected, hasFocus, row, col);
	}
}
