package org.pente.turnBased;

import java.util.Date;

public class TBSet implements java.io.Serializable {

	private long setId;
	
	private TBGame games[] = new TBGame[2];

	private long player1Pid;
	private long player2Pid;
	private long inviterPid;
	
	public static final char STATE_NOT_STARTED = 'N';
	public static final char STATE_ACTIVE = 'A';
	public static final char STATE_COMPLETED = 'C';
	public static final char STATE_CANCEL = 'L';
	public static final char STATE_TIMEOUT = 'T';
	private char state;
	
	private Date creationDate;
	private Date completionDate;
	
    private long cancelPid;
    private String cancelMsg;
    
    private boolean privateGame;
    
	public static final char ANY_RATING = 'A';
	public static final char LOWER_RATING = 'L';
	public static final char HIGHER_RATING = 'H';
	public static final char SIMILAR_RATING = 'S';
	public static final char CLASS_RATING = 'C';
	private char invitationRestriction;

	public TBSet(long setId) {
		this.setId = setId;
	}
	public TBSet(TBGame game1, TBGame game2) {
		this(0, game1, game2);
	}
	public TBSet(long setId, TBGame game1, TBGame game2) {
		this.setId = setId;
		
		this.games[0] = game1;
		this.games[1] = game2;
	}
	
	public void setSetId(long setId) {
		this.setId = setId;
	}
	public long getSetId() {
		return setId;
	}
	public void addGame(TBGame game) {
		if (games[0] == null) {
			games[0] = game;
		}
		else {
			games[1] = game;
		}
	}
	public TBGame getGame1() {
		return games[0];
	}
	public TBGame getGame2() {
		return games[1];
	}
	public TBGame[] getGames() {
		return games;
	}
	public TBGame getLatestMoveGame() {
		if (games[1] == null) {
			return games[0];
		}
		else if (games[0].getLastMoveDate().after(games[1].getLastMoveDate())) {
			return games[0];
		}
		else {
			return games[1];
		}
	}
	public TBGame getGame(long gid) {
		if (games[0].getGid() == gid) return games[0];
		else if (games[1] != null && games[1].getGid() == gid) return games[1];
		else return null;
	}
	public TBGame getOtherGame(long gid) {
		if (games[0].getGid() == gid) return games[1];
		else if (games[1] != null && games[1].getGid() == gid) return games[0];
		else return null;
	}
	public long getInviterPid() {
		return inviterPid;
	}
	public void setInviterPid(long inviterPid) {
		this.inviterPid = inviterPid;
	}
	public long getInviteePid() {
		if (inviterPid == player1Pid) {
			return player2Pid;
		}
		else {
			return player1Pid;
		}
	}
	public void acceptInvite(long pid) {
		if (inviterPid == player1Pid) {
			player2Pid = pid;
		}
		else {
			player1Pid = pid;
		}
		state = STATE_ACTIVE;
	}
	
	public char getState() {
		return state;
	}
	public void setState(char state) {
		this.state = state;
	}
	public char getInvitationRestriction() {
		return invitationRestriction;
	}
	public void setInvitationRestriction(char restriction) {
		this.invitationRestriction = restriction;
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

	public boolean isWaitingSet() {
		return player1Pid == 0 || player2Pid == 0;
	}
	
	public boolean isCompleted() {
		if (state == STATE_COMPLETED) {
			return true;
		}
		else if (games[1] == null) {
			return games[0].isCompleted();
		}
		else {
			return games[0].isCompleted() &&
				   games[1].isCompleted();
		}
	}
	public boolean isTwoGameSet() {
		return games[1] != null;
	}
	public long getWinnerPid() {
		if (!isCompleted()) return 0;
		if (games[1] == null) {
		    if (games[0].isDraw()) return 0;
            else return games[0].getPlayer(games[0].getWinner());
        }
		else if (games[0].isDraw() && !games[1].isDraw()) {
            return games[1].getWinnerPid();
        }
        else if (!games[0].isDraw() && games[1].isDraw()) {
            return games[0].getWinnerPid();
        }
        else if (games[0].getWinnerPid() != games[1].getWinnerPid()) return 0;
		else return games[0].getWinnerPid();
	}
	public boolean isDraw() {
		return isCompleted() && getWinnerPid() == 0;
	}
	public boolean isCancelled() {
	    return state == STATE_CANCEL;
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
    public String getCancelMsg()
    {
        return cancelMsg;
    }
    public void setCancelMsg(String cancelMsg)
    {
        this.cancelMsg = cancelMsg;
    }
    public long getCancelPid()
    {
        return cancelPid;
    }
    public void setCancelPid(long cancelPid)
    {
        this.cancelPid = cancelPid;
    }
	public boolean isPrivateGame() {
		return privateGame;
	}
	public void setPrivateGame(boolean privateGame) {
		this.privateGame = privateGame;
	}
}
