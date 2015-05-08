package org.pente.turnBased.swing;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.text.*;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.*;

import org.pente.game.GridStateFactory;
import org.pente.game.PenteState;
import org.pente.gameDatabase.swing.SwingDSGButton;
import org.pente.gameServer.core.*;
import org.pente.gameServer.client.*;
import org.pente.gameServer.client.awt.*;
import org.pente.gameServer.client.swing.PenteBoardLW;
import org.pente.turnBased.*;


public class TBGamePanel extends JPanel implements OrderedPieceCollection {

	private DateFormat dateFormat;
	// components
    // custom awt
	private CoordinatesListPanel coordinatesList;
	private GameBoard gameBoard;
	
	private boolean showMessages;
	private List messages;
	private String players[] = new String[2];
	private JLabel p1Label, p2Label, p1RatingLabel, p2RatingLabel;
	
    private JEditorPane chatArea;
    private JTextField chatEnter;
    
    private JButton resignButton;
    private JButton cancelButton;
    private int game;
    private int movesMade = 0;
    public static final int DPENTE_START = 1;
    public static final int DPENTE_DECIDE = 2;
    public static final int DPENTE_DECIDED = 3;
    
	private static final GameStyles gameStyles =
        new GameStyles(Color.white, //board back
                       new Color(188, 188, 188), //button back
                       Color.black, //button fore
                       new Color(64, 64, 64), //new Color(0, 102, 255), //button disabled
                       Color.white, //player 1 back
                       Color.black, //player 1 fore
                       Color.black, //player 2 back
                       Color.white, //player 2 fore
                       new Color(188, 188, 188)); //watcher
	
	public TBGamePanel(int gm, String event, final String player1, int player1Rating,
		String player1RatingGif, final String player2, int player2Rating, String player2RatingGif,
		List moves, final boolean myTurn, final boolean showMessages,
		List messages, String timer, boolean rated, Date timeout,
		char state, int winner, Date completionDate, String timezone,
		final int dPenteState, final boolean dPenteSwap, 
		String setStatus, final long otherGame,
		final TBActionHandler actionHandler, String host,
        boolean cancelRequested, boolean privateGame, String color) {

		if (color != null) {
			gameStyles.boardBack = Color.decode(color);
		}
		
		this.game = gm;
		this.messages = messages;
		this.showMessages = showMessages;
		players[0] = player1;
		players[1] = player2;
		
		TimeZone tz = TimeZone.getTimeZone(timezone);
		dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm z");
		dateFormat.setTimeZone(tz);
		
        coordinatesList = new CoordinatesListPanel(gameStyles, 2, new SwingDSGButton());
        coordinatesList.setHighlightColor(Color.blue);
	    coordinatesList.addOrderedPieceCollectionVisitListener(this);
	    coordinatesList.setGame(game);
	    
        PenteBoardLW lw = new PenteBoardLW();
        lw.gridCoordinatesChanged(new AlphaNumericGridCoordinates(19, 19));

	    //OpenGLBoardPanel board = new OpenGLBoardPanel();
		gameBoard = new GameBoard(coordinatesList, lw);

		coordinatesList.gridCoordinatesChanged(gameBoard.getCoordinates());
        coordinatesList.gameOptionsChanged(gameBoard.getGameOptions());
		coordinatesList.setPlayer(1, player1);
		coordinatesList.setPlayer(2, player2);
		
		gameBoard.setGame(game);
        

		chatArea = new JEditorPane("text/html", "");
        chatArea.setEditable(false);
        chatArea.setBackground(Color.white);
        chatArea.setPreferredSize(new Dimension(400, 80));
        chatArea.setMinimumSize(new Dimension(200, 80));
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        
        chatEnter = new JTextField("");
        chatEnter.setBackground(Color.white);
        
		
		if (rated &&
	    	game == GridStateFactory.TB_PENTE ||
            game == GridStateFactory.TB_KERYO ||
            game == GridStateFactory.TB_BOAT_PENTE) {
            ((PenteState) gameBoard.getGridState()).setTournamentRule(rated);
        }
		for (Iterator it = moves.iterator(); it.hasNext();) {
			gameBoard.getGridState().addMove(((Integer) it.next()).intValue());
		}
		visitTurn(coordinatesList.getCurrentTurn());
		if (myTurn) {
			gameBoard.getGridBoard().setThinkingPiecePlayer(
				gameBoard.getGridState().getCurrentPlayer());
		}
		

		final JButton moveButton = new JButton("Make Move");
		moveButton.setEnabled(false);
	    moveButton.setBackground(gameStyles.boardBack);
		final JButton undoButton = new JButton("Undo");
		undoButton.setBackground(gameStyles.boardBack);
		undoButton.setEnabled(false);
		resignButton = new JButton("Resign");
		resignButton.setBackground(gameStyles.boardBack);

        cancelButton = new JButton("Cancel");
        cancelButton.setBackground(gameStyles.boardBack);
		
		final JButton p1Button = new JButton("Play as player 1");
		p1Button.setEnabled(false);
		p1Button.setBackground(gameStyles.boardBack);
		final JButton p2Button = new JButton("Play as player 2");
		p2Button.setEnabled(false);
		p2Button.setBackground(gameStyles.boardBack);

		if (myTurn && game == GridStateFactory.TB_DPENTE &&
			dPenteState == DPENTE_DECIDE) {
			gameBoard.setMessage("D-Pente: You must now choose to play as player 1 or 2");
			p1Button.setEnabled(true);
			p1Button.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					swap();
					p1Button.setEnabled(false);
					p2Button.setEnabled(false);
					gameBoard.setMessage("Swapped, now make your move");
				}
			});
			p2Button.setEnabled(true);
			p2Button.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					p2Button.setEnabled(false);
					p1Button.setEnabled(false);
					resignButton.setEnabled(false);
                    cancelButton.setEnabled(false);
					actionHandler.makeMoves("0", chatEnter.getText());
				}
			});
		}
		else if (game == GridStateFactory.TB_DPENTE &&
			dPenteState == DPENTE_DECIDED) {
			((PenteState) gameBoard.getGridState()).dPenteSwapDecisionMade(
				dPenteSwap);
		}
		
		moveButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (!myTurn) return;
				undoButton.setEnabled(false);
				moveButton.setEnabled(false);
				gameBoard.setMessage(null);
				
				String moves = "";
				if (game == GridStateFactory.TB_DPENTE &&
					dPenteState != DPENTE_DECIDED) {
					if (dPenteState == DPENTE_START) {
						// K10 already done, just send moves 2,3,4
						for (int i = 1; i < 4; i++)
							moves += gameBoard.getGridState().getMove(i) + ",";
					}
					// if we get here it means p2 swapped to become p1
					// and then made a move
					else if (dPenteState == DPENTE_DECIDE) {
						moves = "1," + gameBoard.getGridState().getMove(
							gameBoard.getGridState().getNumMoves() - 1);
					}
				}
				// send 2 moves
				else if (game == GridStateFactory.TB_CONNECT6) {
					moves = gameBoard.getGridState().getMove(
						gameBoard.getGridState().getNumMoves() - 2) + "," +
						gameBoard.getGridState().getMove(
						gameBoard.getGridState().getNumMoves() - 1);
				}
				else {
					moves = gameBoard.getGridState().getMove(
						gameBoard.getGridState().getNumMoves() - 1) + "";
				}
				
				resignButton.setEnabled(false);
                cancelButton.setEnabled(false);
				actionHandler.makeMoves(moves, chatEnter.getText());
			}
		});
		undoButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				gameBoard.getGridState().undoMove();
				movesMade--;
				
				// allow d-pente games 
				if (game == GridStateFactory.TB_DPENTE &&
					gameBoard.getGridState().getNumMoves() > 1 &&
					dPenteState == DPENTE_START) {
					//do nothing
				}
				else if (game == GridStateFactory.TB_CONNECT6 &&
					movesMade > 0) {
					// do nothing
				}
				else {
					undoButton.setEnabled(false);
					gameBoard.setMessage(null);
				}
				moveButton.setEnabled(false);
			}
		});
		resignButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				resignButton.setEnabled(false);
				actionHandler.resignGame(chatEnter.getText());
			}
		});
		

        cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                resignButton.setEnabled(false);
                actionHandler.requestCancel(chatEnter.getText());
            }
        });
		
		gameBoard.addGridBoardListener(new GridBoardListener() {
			public void gridClicked(int x, int y, int button) { 
				if (!myTurn) return;
				if (moveButton.isEnabled()) return;

				int cp = gameBoard.getGridState().getCurrentPlayer();
				int move = (gameBoard.getGridBoard().getGridHeight() - y - 1) * 
            		gameBoard.getGridBoard().getGridWidth() + x;
                
				if (!gameBoard.getGridState().isValidMove(move, cp)) {
                    return;
                }

				movesMade++;
				gameBoard.getGridState().addMove(move);

				//System.out.println("old cp=" + cp);
				//System.out.println("new cp=" + gameBoard.getGridState().getCurrentPlayer());
				
				if (cp == gameBoard.getGridState().getCurrentPlayer()) {
//				// if 1st player is making first 4 moves
//				if (game == GridStateFactory.TB_DPENTE &&
//					dPenteState == DPENTE_START &&
//					gameBoard.getGridState().getNumMoves() < 4) {
//System.out.println("keep going");
					undoButton.setEnabled(true);
				}
				else {
//System.out.println("default handling");
					undoButton.setEnabled(true);
					moveButton.setEnabled(true);
					gameBoard.setMessage("Click Undo or Make Move");
				}
				
				gameBoard.getGridBoard().setThinkingPieceVisible(false);
			}
			public void gridMoved(int x, int y) {
				if (!myTurn) return;
				if (moveButton.isEnabled()) return;
				
				int cp = gameBoard.getGridState().getCurrentPlayer();
                int move = (gameBoard.getGridBoard().getGridHeight() - y - 1) * 
                	gameBoard.getGridBoard().getGridWidth() + x;
//System.out.println("cp="+cp+",move="+move);
                gameBoard.getGridBoard().setThinkingPieceVisible(
					gameBoard.getGridState().isValidMove(move, cp));

                gameBoard.getGridBoard().setThinkingPiecePlayer(
					gameBoard.getGridState().getCurrentColor());
			}
		});

		
		JPanel boardPanel = new JPanel();
		boardPanel.setLayout(new GridBagLayout());
		boardPanel.setBackground(gameStyles.boardBack);
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(4, 4, 4, 4);
		gbc.anchor = GridBagConstraints.NORTH;

		gbc.gridx = 1;
		gbc.gridy = 1;
		gbc.weightx = 10;
		gbc.weighty = 60;
		gbc.gridheight = 2;
		gbc.fill = GridBagConstraints.BOTH;
		boardPanel.add(gameBoard, gbc);

		gbc.gridx = 2;
		gbc.gridy = 1;
		gbc.weightx = 1;
		gbc.weighty = 0;
		gbc.gridheight = 1;
		gbc.fill = GridBagConstraints.NONE;
		boardPanel.add(coordinatesList, gbc);

		if (!event.equals("")) {
			JPanel infoPanel = new JPanel();
			infoPanel.setLayout(new GridBagLayout());
			infoPanel.setBackground(Color.white);
			Border b = new LineBorder(Color.black, 2);
			infoPanel.setBorder(b);
	
			GridBagConstraints gbc3 = new GridBagConstraints();
			gbc3.insets = new Insets(1, 1, 1, 1);
			gbc3.fill = GridBagConstraints.HORIZONTAL;
			gbc3.gridx = 1;
			gbc3.gridy = 1;
			gbc3.weightx = 1;
			gbc3.weighty = 1;
			infoPanel.add(label("Event:"), gbc3);
			gbc3.gridx++;
			gbc3.gridwidth = 2;
			infoPanel.add(label(event), gbc3);
			gbc3.gridx = 1;
			
			gbc3.gridy++;
			gbc3.gridwidth = 1;
			infoPanel.add(label("Player 1:"), gbc3);
			gbc3.gridx++;
			p1Label = new JLabel("<html><body><a href=.>" + player1 + "</a></body></html>");
			p1Label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			p1Label.addMouseListener(new MouseAdapter() {
				public void mouseClicked(MouseEvent e) {
					if (p2Button != null) { p2Button.setEnabled(false); }
					if (p1Button != null) { p1Button.setEnabled(false); }
					resignButton.setEnabled(false);
					moveButton.setEnabled(false);
	                cancelButton.setEnabled(false);
					actionHandler.viewProfile(player1);
				}
			});
			
			infoPanel.add(p1Label, gbc3);
			gbc3.gridx++;
			p1RatingLabel = label("<html><body>" +
					"<img src=http://" + host + "/gameServer/images/" +
					player1RatingGif +
					">&nbsp;&nbsp;" + player1Rating + "</body></html>");
			infoPanel.add(p1RatingLabel, gbc3);
			
			gbc3.gridx = 1;
			gbc3.gridy++;
			infoPanel.add(label("Player 2:"), gbc3);
			gbc3.gridx++;
			p2Label = new JLabel("<html><body><a href=.>" + player2 + "</a></body></html>");
			p2Label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			p2Label.addMouseListener(new MouseAdapter() {
				public void mouseClicked(MouseEvent e) {
					if (p2Button != null) { p2Button.setEnabled(false); }
					if (p1Button != null) { p1Button.setEnabled(false); }
					resignButton.setEnabled(false);
					moveButton.setEnabled(false);
	                cancelButton.setEnabled(false);
					actionHandler.viewProfile(player2);
				}
			});
			infoPanel.add(p2Label, gbc3);
			gbc3.gridx++;
			p2RatingLabel = label("<html><body>" +
					"<img src=http://" + host + "/gameServer/images/" +
					player2RatingGif +
					">&nbsp;&nbsp;" + player2Rating + "</body></html>");
			infoPanel.add(p2RatingLabel, gbc3);
	
			gbc3.gridx = 1;
			gbc3.gridy++;
			infoPanel.add(label("Timer: "), gbc3);
			gbc3.gridx++;
			gbc3.gridwidth = 2;
			infoPanel.add(label(timer), gbc3);
	
			gbc3.gridx = 1;
			gbc3.gridy++;
			gbc3.gridwidth = 1;
			infoPanel.add(label("Rated:"), gbc3);
			gbc3.gridx++;
			gbc3.gridwidth = 2;
			String ratedStr = "No";
			if (rated) {
				if (setStatus != null && !setStatus.equals("")) {
					ratedStr = "Yes (2 game set)";
				}
				else {
					ratedStr = "Yes";
				}
			}
			infoPanel.add(label(ratedStr), gbc3);
	
	        gbc3.gridx = 1;
	        gbc3.gridy++;
	        infoPanel.add(label("Private:"), gbc3);
	        gbc3.gridx++;
	        gbc3.gridwidth = 2;
	        infoPanel.add(label(privateGame ? "Yes" : "No"), gbc3);
	        
			if (rated && setStatus != null && !setStatus.equals("")) {
				gbc3.gridx = 1;
				gbc3.gridy++;
				gbc3.gridwidth = 1;
				infoPanel.add(label("Set status:"), gbc3);
				
				gbc3.gridx++;
				gbc3.gridwidth = 2;
				infoPanel.add(label(setStatus), gbc3);
				
				gbc3.gridx = 1;
				gbc3.gridy++;
				gbc3.gridwidth = 1;
				infoPanel.add(label(""), gbc3);
				
				gbc3.gridx++;
				gbc3.gridwidth = 2;
				JLabel gameLink = label("<html><body><a href=.>view other game in set</a></body></html>");
				gameLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				gameLink.addMouseListener(new MouseAdapter() {
					public void mouseClicked(MouseEvent e) {
						if (p2Button != null) { p2Button.setEnabled(false); }
						if (p1Button != null) { p1Button.setEnabled(false); }
						resignButton.setEnabled(false);
						moveButton.setEnabled(false);
	                    cancelButton.setEnabled(false);
						actionHandler.viewGame(otherGame);
					}
				});
				infoPanel.add(gameLink, gbc3);
			}
			
			if (dPenteState == DPENTE_DECIDED) {
				gbc3.gridx = 1;
				gbc3.gridy++;
				gbc3.gridwidth = 1;
				infoPanel.add(label("Swapped:"), gbc3);
				gbc3.gridx++;
				gbc3.gridwidth = 2;
				infoPanel.add(label(dPenteSwap ? "Yes" : "No"), gbc3);
			}
			
			if (state == 'A') {
				gbc3.gridx = 1;
				gbc3.gridy++;
				gbc3.gridwidth = 1;
				infoPanel.add(label("Must move by:"), gbc3);
				gbc3.gridx++;
				gbc3.gridwidth = 2;
				infoPanel.add(label(dateFormat.format(timeout)), gbc3);
			}
			else if (state == 'C' ||
					 state == 'T') {
	
				gbc3.gridx = 1;
				gbc3.gridy++;
				gbc3.gridwidth = 1;
				infoPanel.add(label("Game Winner:"), gbc3);
				gbc3.gridx++;
				gbc3.gridwidth = 2;
				final String winnerStr = (winner == 1) ? player1 : player2;
				JLabel winnerLabel = (winner == 1 ? 
					label(p1Label.getText()) : label(p2Label.getText()));
				winnerLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				winnerLabel.addMouseListener(new MouseAdapter() {
					public void mouseClicked(MouseEvent e) {
						if (p2Button != null) { p2Button.setEnabled(false); }
						if (p1Button != null) { p1Button.setEnabled(false); }
						resignButton.setEnabled(false);
						moveButton.setEnabled(false);
	                    cancelButton.setEnabled(false);
						actionHandler.viewProfile(winnerStr);
					}
				});
				infoPanel.add(winnerLabel, gbc3);
	
				gbc3.gridx = 1;
				gbc3.gridy++;
				gbc3.gridwidth = 1;
				infoPanel.add(label("Completion Date:"), gbc3);
				gbc3.gridx++;
				gbc3.gridwidth = 2;
				infoPanel.add(label(dateFormat.format(completionDate)), gbc3);
			}
			
			gbc.gridy = 2;
			gbc.gridx = 2;
			gbc.weightx = 1;
			gbc.weighty = 1;
			gbc.gridheight = 1;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.anchor = GridBagConstraints.NORTHWEST;
			boardPanel.add(infoPanel, gbc);
		}
		
		JPanel buttonPanel = new JPanel();
		buttonPanel.setBackground(gameStyles.boardBack);
		buttonPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 2, 0));
		
		if (myTurn) {
			buttonPanel.add(moveButton);
			buttonPanel.add(undoButton);
			buttonPanel.add(resignButton);
			if (game == GridStateFactory.TB_DPENTE &&
				dPenteState == DPENTE_DECIDE) {
				buttonPanel.add(p1Button);
				buttonPanel.add(p2Button);
			}
		}
        if (!cancelRequested && state == 'A') {
            buttonPanel.add(cancelButton);
        }
        
		gbc.gridx = 1;
		gbc.gridy++;
		gbc.weightx = 1;
		gbc.weighty = 0;
		gbc.gridwidth = 2;
		gbc.gridheight = 1;
		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.fill = GridBagConstraints.NONE;
		boardPanel.add(buttonPanel, gbc);

		
		JComponent mainPanel = boardPanel;

		if (showMessages) {
			
			JPanel p = new JPanel();
			p.setBackground(gameStyles.boardBack);
			p.setLayout(new GridBagLayout());
			GridBagConstraints gbc2 = new GridBagConstraints();
			gbc2.insets = new Insets(1, 1, 1, 1);
			gbc2.gridx = 1;
			gbc2.gridy = 1;
			gbc2.weightx = 1;
			gbc2.weighty = 1;
			gbc2.gridwidth = 2;
			gbc2.fill = GridBagConstraints.BOTH;
			p.add(chatScroll, gbc2);
			
			JLabel l = new JLabel("Message:");
			l.setForeground(Color.black);
			gbc2.gridy = 2;
			gbc2.gridwidth = 1;
			gbc2.weightx = 0;
			gbc2.weighty = 0;
			gbc2.fill = GridBagConstraints.NONE;
			p.add(l, gbc2);
			
			gbc2.gridx = 2;
			gbc2.weightx = 1;
			gbc2.fill = GridBagConstraints.BOTH;
			p.add(chatEnter, gbc2);

			gbc.gridy++;
			gbc.gridx = 1;
			gbc.gridheight = 1;
			gbc.gridwidth = 2;
			gbc.fill = GridBagConstraints.BOTH;
			gbc.weighty = 100;
			
			JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                boardPanel, p);
			split.setResizeWeight(1);
			split.setOneTouchExpandable(true);
			split.setBorder(new LineBorder(gameStyles.boardBack, 1));
			mainPanel = split;
		}
		
		setBackground(gameStyles.boardBack);
		setLayout(new BorderLayout());
		add(mainPanel);
	}
	
	//OrderedPieceCollection
	public void addPiece(GridPiece gridPiece, int turn) {}
	public void clearPieces() {}
	public void removePiece(GridPiece gridPiece, int turn) {}
	public void undoLastTurn() {}
	public void visitFirstTurn() {
		visitTurn(0);
	}
	public void visitLastTurn() {
		visitTurn(coordinatesList.getCurrentTurn());
	}
	public void visitNextTurn() {
		visitTurn(coordinatesList.getCurrentTurn());
	}
	public void visitPreviousTurn() {
		visitTurn(coordinatesList.getCurrentTurn());
	}
	public void visitTurn(int turn) {
		if (showMessages && messages != null) {
			chatArea.setText("");
			String newText = "";
			for (Iterator it = messages.iterator(); it.hasNext();) {
				TBMessage m = (TBMessage) it.next();
				if (m.getMoveNum() == turn) {
					String player = m.getPid() == 1 ? players[0] : players[1];
					String msg = player + " (" +
						dateFormat.format(m.getDate()) + "): " + 
						m.getMessage();
					newText += msg + "<br>";
				}
			}
			chatArea.setText(newText);
		}
	}
	//end OrderedPieceCollection
	
	public void destroy() {
		if (coordinatesList != null) {
			coordinatesList.destroy();
		}
		if (gameBoard != null) {
			gameBoard.destroy();
		}
	}
	private JLabel label(String text) {
		JLabel l = new JLabel(text);
		l.setBackground(Color.red);
		return l;
	}
	private void swap() {
		String tmp = players[0];
		players[0] = players[1];
		players[1] = tmp;
		coordinatesList.setPlayer(1, players[0]);
		coordinatesList.setPlayer(2, players[1]);
		
		tmp = p1Label.getText();
		p1Label.setText(p2Label.getText());
		p2Label.setText(tmp);
		
		tmp = p1RatingLabel.getText();
		p2RatingLabel.setText(p1RatingLabel.getText());
		p1RatingLabel.setText(tmp);
		
		for (Iterator it = messages.iterator(); it.hasNext();) {
			TBMessage m = (TBMessage) it.next();
			m.setPid(3 - m.getPid());//switch player seats in messages
		}
		
		((PenteState) gameBoard.getGridState()).dPenteSwapDecisionMade(true);
	}
}
