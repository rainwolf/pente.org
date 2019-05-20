package org.pente.gameServer.tourney;

import java.util.*;

public class TourneyRound {

    private int round;
    private List<TourneySection> sections = new ArrayList<TourneySection>();
    private Tourney tourney;

    public TourneyRound(int round) {
        this.round = round;
    }
    
    public Tourney getTourney() {
        return tourney;
    }
    public void setTourney(Tourney tourney) {
        this.tourney = tourney;
    }

    public int getRound() {
        return round;
    }
    public void setRound(int round) {
        this.round = round;
    }
    public boolean isEmpty() {
        return sections.isEmpty();
    }
    public boolean isComplete() {
        if (isEmpty()) return false;
        for (Iterator sections = getSections().iterator(); sections.hasNext();) {
            TourneySection s = (TourneySection) sections.next();
            if (!s.isComplete()) return false;
        }
        return true;
    }
    
    public void init() {
        for (Iterator sections = getSections().iterator(); sections.hasNext();) {
            TourneySection s = (TourneySection) sections.next();
            s.init();
        }
    }
    public void addSection(TourneySection section) {
        sections.add(section);
        section.setTourneyRound(this);
    }
    public TourneySection getSection(int i) {
        return (TourneySection) sections.get(i - 1);
    }
    public List<TourneySection> getSections() {
        return sections;
    }
    public int getNumSections() {
        return sections.size();
    }
    public List<TourneyPlayerData> getPlayers() {
        List<TourneyPlayerData> players = new ArrayList<>();
        for (Iterator sections = getSections().iterator(); sections.hasNext();) {
            TourneySection s = (TourneySection) sections.next();
            players.addAll(s.getPlayers());
        }
        return players;
    }
    public int getNumPlayers() {
        int sz = 0;
        for (Iterator sections = getSections().iterator(); sections.hasNext();) {
            TourneySection s = (TourneySection) sections.next();
            sz += s.getNumPlayers();
        }
        return sz;
    }
    public int getNumTotalMatches() {
        int sz = 0;
        for (Iterator sections = getSections().iterator(); sections.hasNext();) {
            TourneySection s = (TourneySection) sections.next();
            sz += s.getNumTotalMatches();
        }
        return sz;
    }
    public int getNumCompleteMatches() {
        int sz = 0;
        for (Iterator sections = getSections().iterator(); sections.hasNext();) {
            TourneySection s = (TourneySection) sections.next();
            sz += s.getNumCompleteMatches();
        }
        return sz;
    }
    
    /** for speed tournaments, get the results of a round as strings for
     *  display in the game room
     */
    public List<String> getMatchStrings() {
        List<String> strings = new ArrayList<String>();
        
        if (tourney.getFormat() instanceof DoubleEliminationFormat) {
            strings.add("Bracket 1");
            SingleEliminationSection s = (SingleEliminationSection) sections.get(0);
            for (Iterator it = s.getSingleEliminationMatches().iterator(); it.hasNext();) {
                SingleEliminationMatch m = (SingleEliminationMatch) it.next();
                strings.add(m.getMatchStr());
            }
            if (sections.size() > 1) {
                strings.add("Bracket 2");
                s = (SingleEliminationSection) sections.get(1);
                for (Iterator it = s.getSingleEliminationMatches().iterator(); it.hasNext();) {
                    SingleEliminationMatch m = (SingleEliminationMatch) it.next();
                    strings.add(m.getMatchStr());
                }
            }
        }
        else if (tourney.getFormat() instanceof SingleEliminationFormat) {
            SingleEliminationSection s = (SingleEliminationSection) sections.get(0);
            for (Iterator it = s.getSingleEliminationMatches().iterator(); it.hasNext();) {
                SingleEliminationMatch m = (SingleEliminationMatch) it.next();
                strings.add(m.getMatchStr());
            }
        }
        else if (tourney.getFormat() instanceof SwissFormat) {
            SwissSection s = (SwissSection) sections.get(0);
            for (Iterator it = s.getSwissMatches().iterator(); it.hasNext();) {
                SingleEliminationMatch m = (SingleEliminationMatch) it.next();
                strings.add(m.getMatchStr());
            }
            for (Iterator it = s.getPlayersRanked(tourney).iterator(); it.hasNext();) {
                TourneyPlayerData p = (TourneyPlayerData) it.next();
                strings.add(p.getName() + " " + p.getMatchWins() + "-" + 
                    p.getOpponentWins());
            }
        }
        
        return strings;
    }

    /** return list of affected matches */
    public List forfeitPlayers(long pids[], boolean dropout[]) {
        List updateMatches = new ArrayList();
        for (Iterator sections = getSections().iterator(); sections.hasNext();) {
            TourneySection s = (TourneySection) sections.next();
            updateMatches.addAll(s.forfeitPlayers(pids, dropout));
        }
        return updateMatches;
    }
    
    public void updateAlreadyPlayed(int alreadyPlayed[][]) {
        for (Iterator sections = getSections().iterator(); sections.hasNext();) {
            TourneySection s = (TourneySection) sections.next();
            s.updateAlreadyPlayed(alreadyPlayed);
        }
    }
    
    public List getWinners() {
        List winners = new ArrayList(5);
        for (Iterator sections = getSections().iterator(); sections.hasNext();) {
            TourneySection s = (TourneySection) sections.next();
            winners.addAll(s.getWinners());
        }
        return winners;
    }
}
