package org.pente.gameServer.tourney;

import org.pente.game.GridStateFactory;
import org.pente.gameServer.core.DSGPlayerStorer;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Tourney implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private int eventID;

    private String name;
    private int game;
    private transient TourneyFormat format;
    private int formatType; // 1=RoundRobin, 2=SingleElimination, 3=DoubleElimination, 4=Swiss
    private int status;
    private int initialTime;
    private int incrementalTime;
    private int roundLengthDays;
    private boolean speed;
    private List<Restriction> restrictions = new ArrayList<>();
    private String prize;

    private Date signupEndDate;
    private Date startDate;
    private Date endDate;

    private long forumID;

    /**
     * list of TourneyRounds
     */
    private List<TourneyRound> rounds = new ArrayList<TourneyRound>(3);

    /**
     * list of directors, pids
     */
    private List<Long> directors = new ArrayList<Long>(1);

    /**
     * a matrix of all players containing counts of the number of times
     * the players have played in a round (one match = 1, # of games in match
     * doesn't matter)
     */
    private int alreadyPlayed[][];

    public long getForumID() {
        return forumID;
    }

    public void setForumID(long forumID) {
        this.forumID = forumID;
    }

    public Tourney() {

    }

    public Tourney(int eventID) {
        this.eventID = eventID;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getGame() {
        return game;
    }

    public String getGameName() {
        return GridStateFactory.getDisplayName(game);
    }

    public void setGame(int game) {
        this.game = game;
    }

    public int getIncrementalTime() {
        return incrementalTime;
    }

    public void setIncrementalTime(int incrementalTime) {
        this.incrementalTime = incrementalTime;
    }

    public int getInitialTime() {
        return initialTime;
    }

    public void setInitialTime(int initialTime) {
        this.initialTime = initialTime;
    }

    public int getRoundLengthDays() {
        return roundLengthDays;
    }

    public void setRoundLengthDays(int roundLengthDays) {
        this.roundLengthDays = roundLengthDays;
    }

    public boolean isTurnBased() {
        return this.game > 50;
    }

    public boolean isSpeed() {
        return speed;
    }

    public void setSpeed(boolean speed) {
        this.speed = speed;
    }

    public Date getSignupEndDate() {
        return signupEndDate;
    }

    public void setSignupEndDate(Date signupEndDate) {
        this.signupEndDate = signupEndDate;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public void addRestriction(Restriction r) {
        restrictions.add(r);
    }

    public List<Restriction> getRestrictions() {
        if (restrictions == null) {
            restrictions = new ArrayList<>();
        }
        return restrictions;
    }

    public String getPrize() {
        return prize;
    }

    public void setPrize(String prize) {
        this.prize = prize;
    }

    public void addDirector(long pid) {
        directors.add(Long.valueOf(pid));
    }

    public List<Long> getDirectors() {
        return directors;
    }

    public TourneyFormat getFormat() {
        return format;
    }

    public void setFormat(TourneyFormat format) {
        this.format = format;
        if (format instanceof RoundRobinFormat) {
            formatType = 1;
        } else if (format instanceof DoubleEliminationFormat) {
            formatType = 3;
        } else if (format instanceof SingleEliminationFormat) {
            formatType = 2;
        } else if (format instanceof SwissFormat) {
            formatType = 4;
        } else {
            formatType = 0;
        }
    }

    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        // Restore transient format from the persisted formatType
        if (formatType == 1) {
            format = new RoundRobinFormat();
        } else if (formatType == 2) {
            format = new SingleEliminationFormat();
        } else if (formatType == 3) {
            format = new DoubleEliminationFormat();
        } else if (formatType == 4) {
            format = new SwissFormat();
        }
    }


    public int getEventID() {
        return eventID;
    }

    public void setEventID(int eventID) {
        this.eventID = eventID;
    }

    public TourneyRound createFirstRound(List<TourneyPlayerData> players) {
        TourneyRound r = format.createFirstRound(players, this);
        addRound(r);
        return r;
    }

    public TourneyRound createNextRound(DSGPlayerStorer dsgPlayerStorer) {
        TourneyRound r = format.createNextRound(this, dsgPlayerStorer);
        addRound(r);
        return r;
    }

    public void init() {
        for (TourneyRound round : rounds) {
            round.init();
            if (round.getRound() == 1) {
                alreadyPlayed = new int[round.getNumPlayers() + 1][round.getNumPlayers() + 1];
            }
            round.updateAlreadyPlayed(alreadyPlayed);
        }
    }

    public void addRound(TourneyRound round) {
        rounds.add(round);
        round.setTourney(this);
    }

    public List<TourneyRound> getRounds() {
        return rounds;
    }

    public TourneyRound getRound(int round) {
        return rounds.get(round - 1);
    }

    public TourneyRound getLastRound() {
        return rounds.get(rounds.size() - 1);
    }

    public int getNumRounds() {
        return rounds.size();
    }

    public TourneySection createSection(int section) {
        if (format instanceof RoundRobinFormat) {
            return new RoundRobinSection(section);
        } else if (format instanceof SingleEliminationFormat) {
            return new SingleEliminationSection(section);
        } else if (format instanceof SwissFormat) {
            return new SwissSection(section);
        } else {
            return null;
        }
    }

    public boolean isComplete() {
        return format.isTourneyComplete(this);
    }


    public String getWinner() {
        TourneyPlayerData p = (TourneyPlayerData) getLastRound().getWinners().get(0);
        return p.getName();
    }

    public long getWinnerPid() {
        TourneyPlayerData p = (TourneyPlayerData) getLastRound().getWinners().get(0);
        return p.getPlayerID();
    }

    public int[][] getAlreadyPlayed() {
        if (alreadyPlayed == null) {
            init();
        }
        return alreadyPlayed;
    }
}
