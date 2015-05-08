package org.pente.gameServer.tourney;

public class TourneyPlayerData {

    private long playerID;
    private String name;
    private int totalGames;
    private int rating;
    private int seed;

    private double random;//needed for swiss format
    private int matchWins;//needed for swiss format
    private int opponentWins;//needed for swiss format
    private int matchLosses;//needed for double-elim format
    private int numByes;//needed for single,double-elim,swiss format
    private int numForfeits;//needed for swiss
	
    public int getSeed() {
        return seed;
    }
    public void setSeed(int seed) {
        this.seed = seed;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public long getPlayerID() {
        return playerID;
    }
    public void setPlayerID(long playerID) {
        this.playerID = playerID;
    }
    public int getRating() {
        return rating;
    }
    public void setRating(int rating) {
        this.rating = rating;
    }
    public int getTotalGames() {
        return totalGames;
    }
    public void setTotalGames(int totalGames) {
        this.totalGames = totalGames;
    }
    
    public int getMatchLosses() {
        return matchLosses;
    }
    public void incrementMatchLosses() {
        matchLosses++;
    }
    public void incrementMatchLosses(int count) {
        matchLosses += count;
    }

    public int getMatchWins() {
        return matchWins;
    }
    public void reset() {
        matchWins = 0;
        opponentWins = 0;
        matchLosses = 0;
        numByes = 0;
		numForfeits = 0;
    }
    public void incrementMatchWins() {
        matchWins++;
    }
    public void incrementMatchWins(int count) {
        matchWins += count;
    }

    public int getOpponentWins() {
        return opponentWins;
    }
    public void incrementOpponentWins(int count) {
        this.opponentWins += count;
    }
    
    public int getNumByes() {
        return numByes;
    }
    public void incrementByes() {
        numByes++;
    }

    public double getRandom() {
        return random;
    }
    public void setRandom() {
        this.random = Math.random();
    }
    
	public void incrementForfeits() {
		numForfeits++;
	}
	public int getNumForfeits() {
		return numForfeits;
	}
	
    public boolean equals(Object o) {
        if (!(o instanceof TourneyPlayerData)) return false;
        TourneyPlayerData p = (TourneyPlayerData) o;
        return p.playerID == playerID;
    }
}
