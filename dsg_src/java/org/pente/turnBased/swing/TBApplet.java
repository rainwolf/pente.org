package org.pente.turnBased.swing;

import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import org.pente.turnBased.*;

import javax.swing.*;


public class TBApplet extends JApplet implements TBActionHandler {
	
	private TBGamePanel gamePanel = null;
	private String gid;
    private String sid;
	private JFrame frame;
	private boolean loading = true;
	
	public void init() {
		
		boolean local = getParameter("local") != null;
		
		int game = Integer.parseInt(getParameter("game"));
		gid = getParameter("gid");
		sid = getParameter("sid");
        
		String me = getParameter("me");
		
		if (!local) {
			//JOGLLoader.setupJOGL();
			logClientInfo(me);
		}
		String event = getParameter("event");
		
		String state = getParameter("gameState");
		String dPenteStStr = getParameter("dPenteState");
		int dPenteState = 0;
		if (dPenteStStr != null) {
			dPenteState = Integer.parseInt(dPenteStStr);
		}
		String dPenteSwapStr = getParameter("dPenteSwap");
		boolean dPenteSwap = false;
		if (dPenteSwapStr != null) {
			dPenteSwap = dPenteSwapStr.equals("true");
		}
		String player1 = getParameter("player1");
		String player1RatingStr = getParameter("player1Rating");
		int player1Rating = 0;
		if (player1RatingStr != null) {
			player1Rating = Integer.parseInt(player1RatingStr);
		}
		String player1RatingGif = getParameter("player1RatingGif");
		String player2 = getParameter("player2");
		String player2RatingStr = getParameter("player2Rating");
		int player2Rating = 0;
		if (player2RatingStr != null) {
			player2Rating = Integer.parseInt(player2RatingStr);
		}
		String player2RatingGif = getParameter("player2RatingGif");
		
		String movesStr = getParameter("moves");
		java.util.List movesList = new ArrayList();
		if (movesStr != null) {
			StringTokenizer st = new StringTokenizer(movesStr, ",");
			while (st.hasMoreTokens()) {
				movesList.add(new Integer(st.nextToken()));
			}
		}
		String myTurnStr = getParameter("myTurn");
		boolean myTurn = myTurnStr != null && myTurnStr.equals("true");
		String timer = getParameter("timer");
		
		String ratedStr = getParameter("rated");
		boolean rated = ratedStr != null && ratedStr.equals("true");

        String privateStr = getParameter("private");
        boolean privateGame = privateStr != null && privateStr.equals("true");
        
		String showMessagesStr = getParameter("showMessages");
		boolean showMessages = showMessagesStr != null && showMessagesStr.equals("true");
		
		String messagesStr = getParameter("messages");
		String moveNumsStr = getParameter("moveNums");
		String seqNumsStr = getParameter("seqNums");
		String datesStr = getParameter("dates");
		String playersStr = getParameter("players");
		java.util.List messages = new ArrayList();
		//TODO escape ,'s with other char
		if (messagesStr != null) {
			StringTokenizer st = new StringTokenizer(messagesStr, ",");
			StringTokenizer st2 = new StringTokenizer(moveNumsStr, ",");
			StringTokenizer st3 = new StringTokenizer(datesStr, ",");
			StringTokenizer st4 = new StringTokenizer(seqNumsStr, ",");
			StringTokenizer st5 = new StringTokenizer(playersStr, ",");
			while (st.hasMoreTokens()) {
				TBMessage m = new TBMessage();
				String msg = MessageEncoder.decodeMessage(st.nextToken().trim());
				
				while (true) {
					int hostIndex = msg.indexOf("[host]");
					if (hostIndex == -1) break;
					
					System.out.println("replace [host] with " + getCodeBase().getHost());
					msg = msg.substring(0, hostIndex) +
						getCodeBase().getHost() +
						msg.substring(hostIndex + 6);
				}
				System.out.println("loaded msg=" + msg);
				m.setMessage(msg);
				m.setMoveNum(Integer.parseInt(st2.nextToken().trim()));
				m.setDate(new Date(Long.parseLong(st3.nextToken().trim())));
				m.setSeqNbr(Integer.parseInt(st4.nextToken().trim()));
				// setting seat, not pid...hack
				m.setPid(Integer.parseInt(st5.nextToken().trim()));
				messages.add(m);
			}
		}
		
		String timeoutStr = getParameter("timeout");
		Date timeout = new Date(Long.parseLong(timeoutStr));
		
		Date completedDate = null;
		int winner = Integer.parseInt(getParameter("winner"));
		if (state.charAt(0) == 'C' || 
			state.charAt(0) == 'T') {
			completedDate = new Date(Long.parseLong(getParameter("completedDate")));
		}
			
		String timezone = getParameter("timezone");
		
		try {
	        UIManager.setLookAndFeel(
	            UIManager.getSystemLookAndFeelClassName());
	    } catch (Exception e) {
			e.printStackTrace();
	    }	
		
	    
	    String setStatus = getParameter("setStatus");
	    String otherGameStr = getParameter("otherGame");
	    long otherGame = -1;
	    if (otherGameStr != null && !otherGameStr.equals("")) {
	    	otherGame = Long.parseLong(otherGameStr);
	    }
	    	
	    String host = getCodeBase().getHost();
	    if (host == null || host.equals("")) host = "localhost";
	    
        String crs = getParameter("cancelRequested");
        boolean cancelRequested = crs != null && crs.equals("true");
        
        String color = getParameter("color");
        if (color == null || color.equals("")) color = null;
        
		gamePanel = new TBGamePanel(game, event, player1, player1Rating, player1RatingGif,
			player2, player2Rating, player2RatingGif, movesList, myTurn,
			showMessages, messages, timer, rated, timeout, state.charAt(0),
			winner, completedDate, timezone, dPenteState, dPenteSwap, 
			setStatus, otherGame,
			this, host, cancelRequested, privateGame, color);
		
		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(gamePanel, BorderLayout.CENTER);

		loading = false;
    }
	
	public void logClientInfo(String viewer) {
		
		System.out.println("TBApplet init()");
	    try {
			System.out.println("browser=" + System.getProperty("browser"));
	        System.out.println("java.version=" + System.getProperty("java.version"));
	        System.out.println("java.class.version=" + System.getProperty("java.class.version"));
	        System.out.println("OS=" + System.getProperty("os.name"));
	        System.out.println("OS.version=" + System.getProperty("os.version"));

	        String all = "[" + viewer + "," + gid + "," +
	        	System.getProperty("browser") + "," +
	        	System.getProperty("java.version") + "," +
	        	System.getProperty("java.class.version") + "," +
	        	System.getProperty("os.name") + "," +
	        	System.getProperty("os.version") + "]";

            URL url = new URL(
                "http://" + getCodeBase().getHost() + 
                "/gameServer/tb/log?message=" + URLEncoder.encode(all));
	        url.getContent();

	    } catch (IOException ex) {
	    	System.out.println("error logging client info");
	    	ex.printStackTrace();
        } catch (SecurityException e) {
	    	System.out.println("error retrieving client info");
            e.printStackTrace();
        }
		
	}

	public void makeMoves(String moves, String message) {

        try {
            URL url = new URL(
                "http://" + getCodeBase().getHost() + 
                "/gameServer/tb/game?command=move&gid=" + gid + 
                "&moves=" + moves +
                "&message=" + URLEncoder.encode(message));
            getAppletContext().showDocument(url, "_self");

        } catch (MalformedURLException ex) {
            ex.printStackTrace();
        }
	}
	public void resignGame(String message) {

        try {
            URL url = new URL(
                "http://" + getCodeBase().getHost() + 
                "/gameServer/tb/resign?command=confirm&gid=" + gid + 
                "&message=" + URLEncoder.encode(message));
            getAppletContext().showDocument(url, "_self");

        } catch (MalformedURLException ex) {
            ex.printStackTrace();
        }
	}
    public void requestCancel(String message) {

        try {
            URL url = new URL(
                "http://" + getCodeBase().getHost() + 
                "/gameServer/tb/cancel?command=confirm&sid=" + sid + 
                "&gid=" + gid +
                "&message=" + URLEncoder.encode(message));
            getAppletContext().showDocument(url, "_self");

        } catch (MalformedURLException ex) {
            ex.printStackTrace();
        }
    }
	public void viewProfile(String name) {

        try {
            URL url = new URL(
                "http://" + getCodeBase().getHost() + 
                "/gameServer/profile?viewName=" + name);
            getAppletContext().showDocument(url, "_self");

        } catch (MalformedURLException ex) {
            ex.printStackTrace();
        }
	}
	public void viewGame(long game) {

        try {
            URL url = new URL(
                "http://" + getCodeBase().getHost() + 
                "/gameServer/tb/game?command=load&gid=" + game);
            getAppletContext().showDocument(url, "_self");

        } catch (MalformedURLException ex) {
            ex.printStackTrace();
        }
	}
	
	public void stop() {
	}
	
	public void destroy() {
		if (frame != null) {
			frame.dispose();
		}
		if (gamePanel != null) {
			gamePanel.destroy();
		}
	}
	
	public void start() {
		
	}
}
