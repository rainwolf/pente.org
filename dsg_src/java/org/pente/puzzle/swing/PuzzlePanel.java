package org.pente.puzzle.swing;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.text.*;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.text.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeSelectionModel;

import org.pente.game.GridStateFactory;
import org.pente.game.PenteState;
import org.pente.gameDatabase.swing.*;
import org.pente.gameServer.core.*;
import org.pente.gameServer.client.*;
import org.pente.gameServer.client.awt.*;
import org.pente.gameServer.client.swing.PenteBoardLW;
import org.pente.puzzle.*;
import org.pente.turnBased.*;


public class PuzzlePanel extends JPanel {


	private GameBoard gameBoard;
    private Puzzle puzzle;
    private int cp;
    private int movesMade = 0;
    private Move currentMove;

	private MoveTreeModel currentTreeModel;
	
    private boolean allowMoves = true;
    
    private int tempBlackMove = 1;
    
    public static void main(String args[]) {
    	
    	Puzzle p = new Puzzle();
    	p.setBlackCaps(8);
    	p.setWhiteCaps(0);
    	p.setWhiteMoves(new int[] { 180, 158, 138, 250, 290 });
    	p.setBlackMoves(new int[] { 162, 232, 238, 254 });
    	p.setGame(GridStateFactory.PENTE);
    	p.setCreator("Scott Justice");
    	p.setCreationDate(new Date());
    	
    	Move responseRoot = new Move();
    	Move m1 = new Move(198, 1);
    	responseRoot.addNext(m1);
    	
    	Move m1b = new Move(178, 1);
    	responseRoot.addNext(m1b);
    	Move m1b2 = new Move(198, 2);
    	m1b.addNext(m1b2);
    	
    	//Move g = new Move(200, 2);
    	//Move g2 = new Move(201, 2);
    	//responseRoot.addGenericNext(g);
    	//responseRoot.addGenericNext(g2);
    	
    	Move m2 = new Move(216, 2);
    	m1.addNext(m2);
    	Move m4 = new Move(178, 1);
    	m2.addNext(m4);
    	Move m5 = new Move(198, 2);
    	m4.addNext(m5);
    	Move m6 = new Move(180, 1);
    	m5.addNext(m6);
    	Move m7 = new Move(234, 2);
    	m6.addNext(m7);
    	Move m8 = new Move(274, 1);
    	m7.addNext(m8);
    	Move m9 = new Move(214, 2);
    	m8.addNext(m9);
    	m9.addNext(new Move(196, 1));
    	
    	Move m3 = new Move(178, 2);
    	m1.addNext(m3);
    	Move m10 = new Move(216, 1);
    	m3.addNext(m10);
    	Move m11 = new Move(252, 2);
    	m10.addNext(m11);
    	Move m11b = new Move(234, 2);
    	m10.addNext(m11b);
    	Move m11c = new Move(270, 2);
    	m10.addNext(m11c);
    	Move m11d = new Move(118, 2);
    	m10.addNext(m11d);
    	
    	Move m12 = new Move(212, 1);
    	m11.addNext(m12);
    	Move m13 = new Move(272, 2);
    	m12.addNext(m13);
    	Move m14 = new Move(236, 1);
    	m13.addNext(m14);
    	
    	p.setResponseRoot(responseRoot);
    	
    	PuzzlePanel pp = new PuzzlePanel(p, null);
    	pp.setSize(500, 500);
    	
    	JFrame f = new JFrame("Puzzle");
    	
    	f.getContentPane().add(pp);
    	f.pack();
    	f.setVisible(true);
		f.addWindowListener(new WindowAdapter() {
			
			@Override
			// store bounds/maximized for next startup
			public void windowClosing(WindowEvent e) {
				System.exit(0);
			}
		});
    }
    
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
	
	public PuzzlePanel(final Puzzle puzzle, String color) {

		if (color != null) {
			gameStyles.boardBack = Color.decode(color);
		}
		this.puzzle = puzzle;
		this.currentMove = puzzle.getResponseRoot();
		
        PenteBoardLW lw = new PenteBoardLW();
        lw.gridCoordinatesChanged(new AlphaNumericGridCoordinates(19, 19));

		gameBoard = new GameBoard(null, lw);

		
		gameBoard.setGame(puzzle.getGame());
        gameBoard.getGameOptions().setShowLastMove(false);

		
		if (puzzle.getGame() == GridStateFactory.TB_PENTE ||
			puzzle.getGame() == GridStateFactory.TB_KERYO ||
			puzzle.getGame() == GridStateFactory.TB_BOAT_PENTE) {
            ((PenteState) gameBoard.getGridState()).setTournamentRule(false);
        }
		
		setPuzzleMoves(puzzle);
		
		cp = 1;
		gameBoard.getGridBoard().setThinkingPiecePlayer(1);
		gameBoard.getGridBoard().setThinkingPieceVisible(false);
		
		currentTreeModel = createNewTreeModel();
		final JScrollPane treeScroll = new JScrollPane(currentTreeModel.getJTree());
		treeScroll.getViewport().setMinimumSize(new Dimension(150, 50));
		treeScroll.getViewport().setPreferredSize(new Dimension(150, 250));
		

		currentTreeModel.setComments(new JTextPane());//dummy
		currentTreeModel.newPlunkTree2();
		currentTreeModel.setPuzzle(puzzle);
		currentTreeModel.addListener(new MoveChangeListener() {
			public void changeMoves(int[] moves, PlunkNode current) {
				gameBoard.getGridState().clear();
				setPuzzleMoves(puzzle);

				currentMove = puzzle.getResponseRoot();
				for (int m : moves) {
					gameBoard.getGridState().addMove(m);
					if (currentMove == null || currentMove.getNext() == null) {
						currentMove = null;
					}
					else {
						for (Move move : currentMove.getNext()) {
							if (move.getPosition() == m) {
								currentMove = move;
								break;
							}
						}
					}
				}
			}
			public void nodeChanged() {}
		});
		
		
		//currentTreeModel.addMove(puzzle.getBlackMoves().get(puzzle.getBlackMoves().size() - 1));
		//TODOneed to load currentTreeModel. moves w/ puzzle moves
		
		
		gameBoard.addGridBoardListener(new GridBoardListener() {
			public void gridClicked(int x, int y, int button) { 

				if (!allowMoves) return;
				int m = (18-y)*19+x;
				boolean v = gameBoard.getGridState().isValidMove(m, cp);
				if (!v) return;
		        gameBoard.getGameOptions().setShowLastMove(true);
				gameBoard.getGridState().addMove(m);
				//GridPiece p = new SimpleGridPiece(x, y, cp);
				//gameBoard.getGridBoardComponent().addPiece(p);

				currentTreeModel.addMove(m);
				
				movesMade++;
				if (gameBoard.getGridState().isGameOver()) {
					System.out.println("solved subbranch");
					//jump to next unsolved subbranch or let player navigate there
					return;
				}
				else if (movesMade == puzzle.getWinInMoves()) {
					System.out.println("failed");
					allowMoves = false;
					//print message
					//no more moves allowed on board
					//player can click try again button
					return;
				}
				
//				boolean found = false;
//				if (currentMove != null) {
//					for (Move mv : currentMove.getNext()) {
//						if (mv.getPosition() == m) {
//							currentMove = mv;
//							found = true;
//							break;
//						}
//					}
//				}
				//if (!found) {
				if (currentMove == null) {
//					for (Move mv : currentMove.getGenericNext()) {
//						if (mv.getPosition() != m) {
//							currentMove = mv;
//							break;
//						}
//					}
					System.out.println("not found, find random black position to stall...");
					currentTreeModel.addMove(tempBlackMove++);
					currentMove = null;
				}

				if (currentMove != null) {
					for (Move c : currentMove.getNext()) {
						currentTreeModel.addMove(c.getPosition());
						currentTreeModel.prevMove();
					}
				
					Move n = currentMove.getNext().get(0);
					gameBoard.getGridState().addMove(n.getPosition());
				
					currentTreeModel.addMove(n.getPosition());
				
					currentMove = n;
				}
				
				
				//if (movesMade )
				
				/*
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
				*/
			}
			public void gridMoved(int x, int y) {
				
				int m = (18-y)*19+x;
				System.out.println(m);
				
				boolean v = allowMoves && gameBoard.getGridState().isValidMove(m, cp);
				gameBoard.getGridBoard().setThinkingPieceVisible(v);
				if (v) {
					gameBoard.setCursor(Cursor.HAND_CURSOR);
				}
				else {
					gameBoard.setCursor(Cursor.DEFAULT_CURSOR);
				}
			}
		});

		
		
		setBackground(gameStyles.boardBack);
		setLayout(new BorderLayout());
		add(gameBoard, BorderLayout.WEST);
		add(treeScroll, BorderLayout.EAST);
	}

	private void setPuzzleMoves(final Puzzle puzzle) {
		for (int b : puzzle.getBlackMoves()) {
			GridPiece p = new SimpleGridPiece(b%19,18-b/19, 2);
			gameBoard.getGridBoard().addPiece(p, 1);
			gameBoard.getGridState().setPosition(b, 2);
		}
		for (int w : puzzle.getWhiteMoves()) {
			GridPiece p = new SimpleGridPiece(w%19,18-w/19, 1);
			gameBoard.getGridBoard().addPiece(p, 1);
			gameBoard.getGridState().setPosition(w, 1);
		}
		if (puzzle.getWhiteCaps() > 0) {
			((PenteState) gameBoard.getGridState()).setInitCaptures(2, puzzle.getWhiteCaps());
			for (int i = 0; i < puzzle.getWhiteCaps(); i++) {
				gameBoard.getGridBoard().removePiece(
					new SimpleGridPiece(1, 1, 1), 1);
	        }
		}
		if (puzzle.getBlackCaps() > 0) {
			((PenteState) gameBoard.getGridState()).setInitCaptures(1, puzzle.getBlackCaps());
			for (int i = 0; i < puzzle.getBlackCaps(); i++) {
				gameBoard.getGridBoard().removePiece(
					new SimpleGridPiece(1, 1, 2), 1);
	        }
		}
	}
	
	public void destroy() {

		if (gameBoard != null) {
			gameBoard.destroy();
		}
	}
	private JLabel label(String text) {
		JLabel l = new JLabel(text);
		l.setBackground(Color.red);
		return l;
	}
	

	private MoveTreeModel createNewTreeModel() {
		
		DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");

		final MoveTreeModel tm = new MoveTreeModel(root);
		//tm.setPlunkTree(plunkTree);
		//tm.setComments(comments);
		tm.setGame(puzzle.getGame());
		
		//TODO start with focus on K10
		JTree tree = new JTree(tm);
		tm.setJTree(tree);
		
		tree.addTreeSelectionListener(tm);
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		tree.setScrollsOnExpand(true);
		//tree.addMouseListener(movePopupListener);
		
		MoveIconRenderer mir = new MoveIconRenderer(tm);
		mir.setGame(puzzle.getGame());
		mir.setRotate(false);
		tm.setRenderer(mir);
		
        tree.setCellRenderer(mir);
        tree.setEditable(true);
		MoveEditor moveEditor = new MoveEditor(tree, mir);

		tree.setRowHeight(15);//not sure why i need to
		
		//tm.addListener(moveChangeListener);
		
		return tm;
	}
}
