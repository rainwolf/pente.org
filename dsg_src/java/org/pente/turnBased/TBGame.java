package org.pente.turnBased;

import java.io.Serializable;
import java.util.*;

import org.pente.game.*;
import org.pente.gameServer.core.*;

public class TBGame implements org.pente.game.MoveData, Serializable {
	
	private long gid;
	private long setId;
	
	public static final char STATE_NOT_STARTED = 'N';
	public static final char STATE_ACTIVE = 'A';
	public static final char STATE_COMPLETED = 'C';
	public static final char STATE_COMPLETED_TO = 'T';
	public static final char STATE_CANCEL = 'L';
	private char state;
	
	private long player1Pid;
	private long player2Pid;

    private boolean draw;
	private int winner;
	
	private Date creationDate;
	private Date startDate;
	private Date lastMoveDate;
	private Date timeoutDate;
	private Date completionDate;
	
	private int game;
	private int eventId;
	private int round;
	private int section;
	
	private int daysPerMove;
	private boolean rated;
	
	private boolean undoRequested;
	private byte hiddenBy = 0;

	private List<Integer> moves = new ArrayList<Integer>();
	private List<TBMessage> messages = new ArrayList<TBMessage>();
	
    public static final int DPENTE_STATE_START = 1;
    public static final int DPENTE_STATE_DECIDE = 2;
    public static final int DPENTE_STATE_DECIDED = 3;
	private int dPenteState = DPENTE_STATE_START;
	private boolean dPenteSwapped = false;
	
	public static final int GO_PLAY = 1;
	public static final int GO_MARK_DEAD_STONES = 2;
	public static final int GO_EVALUATE_DEAD_STONES = 3;
	private int goState;

    public int getGoState() { return goState; }
    public void setGoState(int goState) { this.goState = goState; }

    private TBSet tbSet;
	
	public void timeout() {
		completionDate = new Date();
		state = STATE_COMPLETED_TO;
	}
	public void end() {
		completionDate = new Date();
		state = STATE_COMPLETED;
	}

	public Date getCompletionDate() {
		return completionDate;
	}
	public void setCompletionDate(Date completionDate) {
		this.completionDate = completionDate;
	}
	public Date getCreationDate() {
		return creationDate;
	}
	public void setCreationDate(Date creationDate) {
		this.creationDate = creationDate;
	}
	public int getDaysPerMove() {
		return daysPerMove;
	}
	public void setDaysPerMove(int daysPerMove) {
		this.daysPerMove = daysPerMove;
	}
	public boolean isRated() {
		return rated;
	}
	public void setRated(boolean rated) {
		this.rated = rated;
	}

	public int getEventId() {
		return eventId;
	}
	public void setEventId(int eventId) {
		this.eventId = eventId;
	}
	public int getGame() {
		return game;
	}
	public void setGame(int game) {
		this.game = game;
	}
	public long getGid() {
		return gid;
	}
	public void setGid(long gid) {
		this.gid = gid;
	}
	public long getSetId() {
		return setId;
	}
	public void setSetId(long setId) {
		this.setId = setId;
	}
	public Date getLastMoveDate() {
		return lastMoveDate;
	}
	public void setLastMoveDate(Date lastMoveDate) {
		this.lastMoveDate = lastMoveDate;
	}
	
	public List<TBMessage> getMessages() {
		return messages;
	}
	public void setMessages(List<TBMessage> messages) {
		this.messages = messages;
	}
	public void addMessage(TBMessage m) {
		messages.add(m);
	}
	public TBMessage getMessage(int moveNum) {
		for (TBMessage m : messages) {
			if (m.getMoveNum() == moveNum) return m;
		}
		return null;
	}

	public TBMessage getInviterMessage() {
		//TODO hackish
		if (messages.isEmpty()) return null;
		return messages.get(0);
	}
	
	public int[] getMoves() {
		int m[] = new int[moves.size()];
		for (int i = 0; i < moves.size(); i++) {
			m[i] = moves.get(i);
		}
		return m;
	}
	public void undoMove() {
		moves.remove(moves.size()-1);
	}
	public int getMove(int num) {
		return moves.get(num);
	}
	public List<Integer> getMovesList() {
		return moves;
	}
	public void setMoves(List<Integer> moves) {
		this.moves = moves;
	}
	public void addMove(int move) {
		moves.add(move);
		
		lastMoveDate = new Date();
	}
	public long getCurrentPlayer() {
//TODO why couldn't i just instantiate a gridstate and use that?
		int cp = 0;
		if ((game == GridStateFactory.TB_DPENTE || game == GridStateFactory.TB_DKERYO) &&
			dPenteState != DPENTE_STATE_DECIDED) {
			if (dPenteState == DPENTE_STATE_START) {
				cp = 1;
			}
			else if (dPenteState == DPENTE_STATE_DECIDE) {
				cp = 2;
			} else {
				cp = -1;
			}
		}  else if (game == GridStateFactory.TB_CONNECT6) {
	    	cp = ((moves.size() + 1) / 2) % 2 + 1;
		} else {
			cp = moves.size() % 2 + 1;
		}
		if (cp == 1) {
			return player1Pid;
		} else if (cp == 2) {
			return player2Pid;
		}
		return -1;
	}
	public int getNumMoves() {
		return moves.size();
	}
	
	public long getPlayer(int seat) {
		if (seat == 1) return player1Pid;
		else return player2Pid;
	}
	public int getPlayerSeat(long pid) {
		if (player1Pid == pid) return 1;
		else if (player2Pid == pid) return 2;
		else return 0;
	}
	public long getPlayer1Pid() {
		return player1Pid;
	}
	public void setPlayer1Pid(long player1Pid) {
		this.player1Pid = player1Pid;
	}
	public long getPlayer2Pid() {
		return player2Pid;
	}
	public void setPlayer2Pid(long player2Pid) {
		this.player2Pid = player2Pid;
	}
	public long getOpponent(long pid) {
		if (player1Pid == pid) return player2Pid;
		else return player1Pid;
	}

	public int getRound() {
		return round;
	}
	public void setRound(int round) {
		this.round = round;
	}
	public int getSection() {
		return section;
	}
	public void setSection(int section) {
		this.section = section;
	}
	public Date getStartDate() {
		return startDate;
	}
	public void setStartDate(Date startDate) {
		this.startDate = startDate;
	}
	public boolean isCompleted() {
		return state == STATE_COMPLETED ||
		       state == STATE_COMPLETED_TO;
	}
    public boolean isDraw() {
        return draw;
    }
    public void setDraw(boolean draw) {
        this.draw = draw;
    }
	public char getState() {
		return state;
	}
	public void setState(char state) {
		this.state = state;
	}
	public Date getTimeoutDate() {
		return timeoutDate;
	}
	public void setTimeoutDate(Date timeoutDate) {
		this.timeoutDate = timeoutDate;
	}
	
	public void acceptInvite(long pid, long inviterPid) {
		if (inviterPid == player1Pid) {
			player2Pid = pid;
		}
		else {
			player1Pid = pid;
		}
		startDate = new Date();
		lastMoveDate = startDate;
		state = STATE_ACTIVE;
	}

    public long getWinnerPid() {
        if (winner == 1) {
            return player1Pid;
        }
        else {
            return player2Pid;
        }
    }
	public boolean isWinner(long pid) {
		if (pid == player1Pid) return winner == 1;
		else return winner == 2;
	}
	public int getWinner() {
		return winner;
	}
	public void setWinner(int winner) {
		this.winner = winner;
        if (state == STATE_COMPLETED && winner == 0) draw = true;
	}

	public int getDPenteState() {
		return dPenteState;
	}
	public void setDPenteState(int penteState) {
		dPenteState = penteState;
	}

	public boolean didDPenteSwap() {
		return dPenteSwapped;
	}
	public void setDPenteSwapped(boolean penteSwapped) {
		dPenteSwapped = penteSwapped;
	}
	public void dPenteSwap(boolean swap) {
		setDPenteSwapped(swap);
		setDPenteState(DPENTE_STATE_DECIDED);
		
		if (swap) {
			long tmp = getPlayer1Pid();
			setPlayer1Pid(getPlayer2Pid());
			setPlayer2Pid(tmp);
		}

		lastMoveDate = new Date();
	}

	public boolean isUndoRequested() { return undoRequested; }
	public void setUndoRequested(boolean undoRequested) { this.undoRequested = undoRequested; }
	public byte getHiddenBy() { return hiddenBy; }
	public void setHiddenBy(byte hider) { this.hiddenBy = hider; }
	public boolean canHide(long pid) {
		if (getCurrentPlayer() == pid && !isHidden()) {
			return true;
		}
		return false;
	}
	public boolean canUnHide(long pid) {
		if (isHidden()) {
			long hiderPid = (getHiddenBy()==1?player1Pid:player2Pid);
			return hiderPid == pid;
		}
		return false;
	}
	public boolean isHidden() {
		return (getHiddenBy() != 0);
	}


//	public int hashCode() {
//		return (int) gid;
//	}
//	public boolean equals(Object obj) {
//		if (!(obj instanceof TBGame)) return false;
//		TBGame g = (TBGame) obj;
//		return g.gid == gid;
//	}
	public TBSet getTbSet() {
		return tbSet;
	}
	public void setTbSet(TBSet tbSet) {
		this.tbSet = tbSet;
	}
	
	public GameData convertToGameData(DSGPlayerStorer dsgPlayerStorer) throws DSGPlayerStoreException {
		GameData gameData = new DefaultGameData();
		gameData.setGameID(getGid());
        gameData.setDate(getCompletionDate() == null ? new Date() : getCompletionDate());
        gameData.setGame(GridStateFactory.getGameName(getGame()));
		gameData.setSite("Pente.org");
        gameData.setEvent("Turn-based Game");
        
		gameData.setInitialTime(getDaysPerMove());
		gameData.setRated(isRated());
		gameData.setTimed(true);
		gameData.setPrivateGame(tbSet.isPrivateGame());
    
		DSGPlayerData player1 = dsgPlayerStorer.loadPlayer(getPlayer1Pid());

        DSGPlayerGameData p1Data = player1.getPlayerGameData(getGame());

		PlayerData p1 = new DefaultPlayerData();
        p1.setType(PlayerData.HUMAN);
        p1.setRating((int) p1Data.getRating());
		p1.setUserIDName(player1.getName());
		p1.setUserID(player1.getPlayerID());
		gameData.setPlayer1Data(p1);

		DSGPlayerData player2 = dsgPlayerStorer.loadPlayer(getPlayer2Pid());

        DSGPlayerGameData p2Data = 
            player2.getPlayerGameData(getGame());
		PlayerData p2 = new DefaultPlayerData();
        p2.setType(PlayerData.HUMAN);
        p2.setRating((int) p2Data.getRating());
		p2.setUserIDName(player2.getName());
		p2.setUserID(player2.getPlayerID());
		gameData.setPlayer2Data(p2);
		

        gameData.setWinner(getWinner());

        if (getGame() == GridStateFactory.TB_DPENTE || getGame() == GridStateFactory.TB_DKERYO) {
            gameData.setSwapped(didDPenteSwap());
        }

        for (int i = 0; i < getNumMoves(); i++) {
            gameData.addMove(getMove(i));
        }
        
        return gameData;
	}

    @Override
    public boolean equals(Object o) {
	    if (o == null) {
	        return false;
        }
        if (!(o instanceof TBGame)) {
            return false;
        }
        TBGame g = (TBGame) o;
        return this.player1Pid == g.getPlayer1Pid() && 
                this.player2Pid == g.getPlayer2Pid() && 
                this.game == g.getGame() &&
                this.state == STATE_NOT_STARTED &&
                g.getState() == STATE_NOT_STARTED &&
                this.daysPerMove == g.getDaysPerMove() &&
                this.rated == g.isRated();
    }

    @Override
    public int hashCode() {
	    int PRIME = 97;
	    int tmp = (int) player1Pid;
	    tmp = (int) player2Pid + PRIME*tmp;
        tmp = game + PRIME*tmp;
        tmp = state + PRIME*tmp;
        tmp = daysPerMove + PRIME*tmp;
        tmp = (rated?1:0) + PRIME*tmp;
        return tmp;
//        return PRIME;
    }
}
