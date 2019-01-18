package org.pente.gameServer.tourney;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class RoundRobinSection extends TourneySection {
    
    private int numPlayers = -1;
    private List players = new ArrayList();
    private boolean winners[];
    private long results[][];
    
    private List matches = new ArrayList();
    
    public RoundRobinSection(int section) {
        super(section);
    }
    
    // relies on sql to pull matches in correct order
    public void addMatch(TourneyMatch match) {
        matches.add(match);
    }

    public List getMatches() {
        return matches;
    }
    
    public int getNumPlayers() {
        if (numPlayers != -1) return numPlayers;
        
        switch (getMatches().size()) {
        case 2: return numPlayers = 2;
        case 6: return numPlayers = 3;
        case 12: return numPlayers = 4;
        case 20: return numPlayers = 5;
        case 30: return numPlayers = 6;
        default: return -1;
        }
    }

    public List getPlayers() {
        return players;
    }
    /** Creates a matrix of results for display */
    public long[][] getResultsMatrix() {
        
        // one row for each player
        // 3 columns per game, result+forfeit+gid, final column is total wins
        long r[][] = new long[getNumPlayers()][getNumPlayers() * 6 + 1];
        
        for (int i = 0; i < getNumPlayers(); i++) {
            int total = 0;
            for (int j = 0; j < getNumPlayers(); j++) {
                if (i == j) continue;
                r[i][j * 6] = results[i][j * 3];
                r[i][j * 6 + 1] = results[i][j * 3 + 1];
                r[i][j * 6 + 2] = results[i][j * 3 + 2];
                if (r[i][j * 6] == 1) total++;
                
                r[i][j * 6 + 3] = results[j][i * 3];
                r[i][j * 6 + 4] = results[j][i * 3 + 1];
                r[i][j * 6 + 5] = results[j][i * 3 + 2];
                if (r[i][j * 6 + 3] == 2) total++;
            }
            
            r[i][getNumPlayers() * 6] = total;
        }

        return r;
    }
    
    public void init() {
        
        if (getMatches().isEmpty()) return;
        players.clear();
        
        // store names, pids for later use
        for (int i = 0, j = 0;
             i < getMatches().size(); 
             i += (getNumPlayers() - 1), j++) {
            TourneyMatch match = (TourneyMatch) getMatches().get(i);
            players.add(match.getPlayer1());
        }
        
        // these help determine any winners
        int wins[] = new int[getNumPlayers()];
        int totalGames[] = new int[getNumPlayers()];
        int possibleWins[] = new int[getNumPlayers()];

        //setup 2d results matrix showing wins+forfeits+gid across a row
        results = new long[getNumPlayers()][getNumPlayers() * 3];
        int m = 0;
        for (int i = 0; i < getNumPlayers(); i++) {
            for (int j = 0; j < getNumPlayers(); j++) {
                if (i == j) continue;
                TourneyMatch match = (TourneyMatch) getMatches().get(m++);
                results[i][j * 3] = match.getResult();
                results[i][j * 3 + 1] = match.isForfeit() ? 1 : 0;
                results[i][j * 3 + 2] = match.getGid();
                // dbl-forfeits included here
                if (results[i][j * 3] != 0) {
                    totalGames[i]++;
                    totalGames[j]++;
                }
                if (results[i][j * 3] == 1) wins[i]++;
                else if (results[i][j * 3] == 2) wins[j]++;
            }
        }
        
        //determine winners if any
        //alg. is as follows
        //figure out for each player: num wins, num games played, possible wins
        //possible wins assumes the player wins the rest of their unplayed games
        //then a player is a winner only if their wins >= all other players
        //possible wins.  it is possible that one winner could be determined
        //while another player still has the chance to tie
        winners = new boolean[getNumPlayers()];
        int numGames = (getNumPlayers() - 1) * 2;
        for (int i = 0; i < getNumPlayers(); i++) {
            possibleWins[i] = wins[i] + (numGames - totalGames[i]);
        }
        
        // in all rounds except the final one, allow at least
        // top 2 players to be winners
        int numWinnersInSection = 0;
        if (round.getTourney().getName().equals("Pente - March 2005 Open")) {
        	numWinnersInSection = 1; // this tourney didn't have the above rule
        }
        // final round means final 2 players
        else if (round.getNumSections() == 1 && 
                round.getSection(1).getNumPlayers() == 2) {
                numWinnersInSection = 1;
        }
        else {
            numWinnersInSection = 2;
        }
        
        outer: for (int i = 0; i < getNumPlayers(); i++) {
        	int possiblePlayersAbove = 0;
            for (int j = 0; j < getNumPlayers(); j++) {
                if (i == j) continue;
                if (wins[i] < possibleWins[j]) {
                    if (++possiblePlayersAbove >= numWinnersInSection) {
                    	continue outer;
                    }
                }
            }
            //if we made it here we have a winner
            winners[i] = true;
        }

        if (round.getNumSections() == 1) {
            boolean moreThanOneMaxScore = true;
            long currentMaxWins = 0;
            int maxIdx = -1;
            for (int i = 0; i < getNumPlayers(); i++) {
//                long score = results[i][results[i].length - 1];
                long score = wins[i];
                if (score > currentMaxWins) {
                    currentMaxWins = score;
                    moreThanOneMaxScore = false;
                    maxIdx = i;
                } else if (score == currentMaxWins) {
                    currentMaxWins = score;
                    moreThanOneMaxScore = true;
                }
            }
            if (!moreThanOneMaxScore) {
                for (int i = 0; i < winners.length; i++) {
                    if (i != maxIdx) {
                        winners[i] = false;
                    }
                }
            }
        }
    }
    
    public List getWinners() {
        ArrayList l = new ArrayList();
        for (int i = 0; i < getNumPlayers(); i++) {
            if (winners[i]) l.add(players.get(i));
        }
        return l;
    }
    public boolean isWinner(int i) {
        return winners[i];
    }
   
    public void updateAlreadyPlayed(int alreadyPlayed[][]) {
        for (Iterator it = players.iterator(); it.hasNext();) {
            TourneyPlayerData p1 = (TourneyPlayerData) it.next();
            for (Iterator it2 = players.iterator(); it2.hasNext();) {
                TourneyPlayerData p2 = (TourneyPlayerData) it2.next();
                if (p1.getSeed() != p2.getSeed()) {
                    alreadyPlayed[p1.getSeed()][p2.getSeed()]++;
                }
            }
        }
    }
}
