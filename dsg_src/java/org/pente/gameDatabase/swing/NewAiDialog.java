package org.pente.gameDatabase.swing;

import java.awt.*;
import java.awt.event.*;
import java.util.List;

import javax.swing.*;

import org.pente.game.*;
import org.pente.gameDatabase.swing.component.*;

public class NewAiDialog extends MyDialog {
	
	private int level = 1;
	private int seat = 1;
	private int vct = 0;
	private long treeId = 0;
	
	private JComboBox treeChoice;
	private JComboBox gameChoice;
	private JComboBox seatChoice;
	private JComboBox levelChoice;
	private JComboBox vctChoice;
	private int game;
	

	public NewAiDialog(Frame owner, int gm, int level, int vct, 
		final List<PlunkTree> trees, long treeId) {
		super(owner, "New Game", true);
		
		this.game = gm;
		this.level = level;
		this.vct = vct;
		this.treeId = treeId;
		
		
		gameChoice = new JComboBox();
		gameChoice.addItem(GridStateFactory.PENTE_GAME.getName());
		gameChoice.addItem(GridStateFactory.KERYO_GAME.getName());
		
		if (gm == -1) {
			gameChoice.setSelectedIndex(0);
			game = 1;
		}
		else {
			gameChoice.setSelectedItem(GridStateFactory.getGameName(gm));
			game = gm;
		}
		
		gameChoice.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				if (e.getStateChange() == ItemEvent.SELECTED) {
					NewAiDialog.this.game = GridStateFactory.getGameId((String) 
						gameChoice.getSelectedItem());
				}
			}
		});
		
		treeChoice = new JComboBox();
		treeChoice.addItem("No Opening Book");
		for (PlunkTree t : trees) {
			treeChoice.addItem(t.getName());
			if (t.getTreeId() == treeId) {
				treeChoice.setSelectedItem(t.getName());
			}
		}

		treeChoice.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				if (e.getStateChange() == ItemEvent.SELECTED) {
					String treeName = (String) treeChoice.getSelectedItem();
					if (treeName.equals("No Opening Book")) {
						NewAiDialog.this.treeId = -1;
					}
					else {
						for (PlunkTree t : trees) {
							if (t.getName().equals(treeName)) {
								NewAiDialog.this.treeId = t.getTreeId();
								break;
							}
						}
					}
				}
			}
		});
		
		
		levelChoice = new JComboBox();
		for (int i = 1; i < 13; i++) {
			levelChoice.addItem("Level " + Integer.toString(i));
		}
		levelChoice.setSelectedIndex(level - 1);
		levelChoice.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				if (e.getStateChange() == ItemEvent.SELECTED) {
					NewAiDialog.this.level = levelChoice.getSelectedIndex() + 1;
				}
			}
		});
		seatChoice = new JComboBox();
		seatChoice.addItem("Player 1");
		seatChoice.addItem("Player 2");
		seatChoice.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				if (e.getStateChange() == ItemEvent.SELECTED) {
					NewAiDialog.this.seat = seatChoice.getSelectedIndex() + 1;
				}
			}
		});
		vctChoice = new JComboBox();
		vctChoice.addItem("VCT Off");
		vctChoice.addItem("VCT On");
		vctChoice.setSelectedIndex(vct);
		vctChoice.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				if (e.getStateChange() == ItemEvent.SELECTED) {
					NewAiDialog.this.vct = vctChoice.getSelectedIndex();
				}
			}
		});
		
		
		JPanel top = new JPanel();
		top.setLayout(new GridBagLayout());
		top.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(4, 4, 4, 4);

		gbc.gridx = 1;
		gbc.gridy = 1;
		gbc.gridheight = 1;
		gbc.weighty = 1;
		gbc.weightx = 1;
		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.fill = GridBagConstraints.BOTH;
		top.add(new JLabel("Game:"), gbc);
		gbc.gridx = 2;
		gbc.anchor = GridBagConstraints.NORTHWEST;
		top.add(gameChoice, gbc);
		
		gbc.gridy++;
		gbc.gridx = 1;
		top.add(new JLabel("Play as:"), gbc);
		gbc.gridx = 2;
		gbc.anchor = GridBagConstraints.NORTHWEST;
		top.add(seatChoice, gbc);
		
		gbc.gridy++;
		gbc.gridx = 1;
		top.add(new JLabel("Computer Level:"), gbc);
		gbc.gridx = 2;
		gbc.anchor = GridBagConstraints.NORTHWEST;
		top.add(levelChoice, gbc);
		
		gbc.gridy++;
		gbc.gridx = 1;
		top.add(new JLabel("VCT Search:"), gbc);
		gbc.gridx = 2;
		gbc.anchor = GridBagConstraints.NORTHWEST;
		top.add(vctChoice, gbc);
		
		gbc.gridy++;
		gbc.gridx = 1;
		top.add(new JLabel("Opening Book:"), gbc);
		gbc.gridx = 2;
		gbc.anchor = GridBagConstraints.NORTHWEST;
		top.add(treeChoice, gbc);
		
		
		getContentPane().add(top, BorderLayout.NORTH);
		
		JPanel p = new JPanel();
		p.setLayout(new FlowLayout(FlowLayout.CENTER, 2, 2));
		p.add(ok);
		p.add(cancel);
		
		getContentPane().add(p, BorderLayout.SOUTH);
		pack();

		centerDialog(owner);
		setVisible(true);
	}

	public int getGame() {
		return game;
	}
	
	public int getLevel() {
		return level;
	}

	public int getSeat() {
		return seat;
	}

	public int getVct() {
		return vct;
	}
	public long getTreeId() {
		return treeId;
	}
}
