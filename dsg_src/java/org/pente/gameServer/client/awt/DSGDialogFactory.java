package org.pente.gameServer.client.awt;

import java.awt.*;

import org.pente.gameServer.client.GameStyles;

public class DSGDialogFactory {
	
	public static DSGDialog createUndoDialog(Frame f, GameStyles gameStyles) {
		return new DSGDialog(f,
							 "Undo Requested",
							 gameStyles, 
							 "Your opponent has requested his/her last move be undone.",
							 "Is this acceptable?",
							 "Yes",
							 "No",
							 true);
	}
	
	public static DSGDialog createCancelDialog(Frame f, GameStyles gameStyles, String txt) {
		return new DSGDialog(f,
							 "Cancel Requested",
							 gameStyles,
        					 "Your opponent has requested that this " + txt + " be cancelled.",
        					 "Is this acceptable?",
        					 "Yes",
        					 "No",
        					 true);
	}
	
	public static DSGDialog createResignDialog(Frame f, GameStyles gameStyles) {
		return new DSGDialog(f,
							 "Resign",
							 gameStyles,
							 "Are you sure you want to resign?",
							 null,
							 "Yes",
							 "No",
							 true);
	}
    public static DSGDialog createSwapDialog(Frame f, GameStyles gameStyles) {
        return new DSGDialog(f, "Swap Seats", gameStyles,
            "Do you want to play this game as player 1 or player 2?",
            null, "Player 1", "Player 2", false);
    }
    public static DSGDialog createGoStateDialog(Frame f, GameStyles gameStyles, String txt) {
        return new DSGDialog(f, "Evaluate board", gameStyles,
                "Do you accept the marked dead stones and current score?",
                txt, "Accept", "Reject", false);
    }
}

