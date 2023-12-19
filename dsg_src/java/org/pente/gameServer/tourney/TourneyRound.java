package org.pente.gameServer.tourney;

import java.util.*;

public class TourneyRound {

    private int round;
    private List<TourneySection> sections = new ArrayList<>();
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
        for (Iterator<TourneySection> sections = getSections().iterator(); sections.hasNext(); ) {
            TourneySection s = sections.next();
            if (!s.isComplete()) return false;
        }
        return true;
    }

    public void init() {
        for (Iterator<TourneySection> sections = getSections().iterator(); sections.hasNext(); ) {
            TourneySection s = sections.next();
            s.init();
        }
    }

    public void addSection(TourneySection section) {
        sections.add(section);
        section.setTourneyRound(this);
    }

    public TourneySection getSection(int i) {
        return sections.get(i - 1);
    }

    public List<TourneySection> getSections() {
        return sections;
    }

    public int getNumSections() {
        return sections.size();
    }

    public List<TourneyPlayerData> getPlayers() {
        List<TourneyPlayerData> players = new ArrayList<>();
        for (Iterator<TourneySection> sections = getSections().iterator(); sections.hasNext(); ) {
            TourneySection s = sections.next();
            players.addAll(s.getPlayers());
        }
        return players;
    }

    public int getNumPlayers() {
        int sz = 0;
        for (Iterator<TourneySection> sections = getSections().iterator(); sections.hasNext(); ) {
            TourneySection s = sections.next();
            sz += s.getNumPlayers();
        }
        return sz;
    }

    public int getNumTotalMatches() {
        int sz = 0;
        for (Iterator<TourneySection> sections = getSections().iterator(); sections.hasNext(); ) {
            TourneySection s = sections.next();
            sz += s.getNumTotalMatches();
        }
        return sz;
    }

    public int getNumCompleteMatches() {
        int sz = 0;
        for (Iterator<TourneySection> sections = getSections().iterator(); sections.hasNext(); ) {
            TourneySection s = sections.next();
            sz += s.getNumCompleteMatches();
        }
        return sz;
    }

    /**
     * for speed tournaments, get the results of a round as strings for
     * display in the game room
     */
    public List<String> getMatchStrings() {
        List<String> strings = new ArrayList<String>();

        if (tourney.getFormat() instanceof DoubleEliminationFormat) {
            strings.add("Bracket 1");
            SingleEliminationSection s = (SingleEliminationSection) sections.get(0);
            for (Iterator<SingleEliminationMatch> it = s.getSingleEliminationMatches().iterator(); it.hasNext(); ) {
                SingleEliminationMatch m = it.next();
                strings.add(m.getMatchStr());
            }
            if (sections.size() > 1) {
                strings.add("Bracket 2");
                s = (SingleEliminationSection) sections.get(1);
                for (Iterator<SingleEliminationMatch> it = s.getSingleEliminationMatches().iterator(); it.hasNext(); ) {
                    SingleEliminationMatch m = it.next();
                    strings.add(m.getMatchStr());
                }
            }
        } else if (tourney.getFormat() instanceof SingleEliminationFormat) {
            SingleEliminationSection s = (SingleEliminationSection) sections.get(0);
            for (Iterator<SingleEliminationMatch> it = s.getSingleEliminationMatches().iterator(); it.hasNext(); ) {
                SingleEliminationMatch m = it.next();
                strings.add(m.getMatchStr());
            }
        } else if (tourney.getFormat() instanceof SwissFormat) {
            SwissSection s = (SwissSection) sections.get(0);
            for (Iterator<SingleEliminationMatch> it = s.getSwissMatches().iterator(); it.hasNext(); ) {
                SingleEliminationMatch m = it.next();
                strings.add(m.getMatchStr());
            }
            for (Iterator<TourneyPlayerData> it = s.getPlayersRanked(tourney).iterator(); it.hasNext(); ) {
                TourneyPlayerData p = it.next();
                strings.add(p.getName() + " " + p.getMatchWins() + "-" +
                        p.getOpponentWins());
            }
//        } else if (tourney.getFormat() instanceof RoundRobinFormat) {
//            int i = 1;
//            for(TourneySection s: sections) {
//                strings.add("Section "+i);
//                for(TourneyMatch m: s.getMatches()) {
//                    if (!m.isBye()) {
//                        String r = m.getPlayer1().getName() + "(" + m.getPlayer1().getSeed() + ")" +
//                                " " + m.getPlayer2().getName() + "(" + m.getPlayer2().getSeed() + ")";
//                        strings.add(r);
//                    }
//                }
//            }
        }

        return strings;
    }

    /**
     * return list of affected matches
     */
    public List<TourneyMatch> forfeitPlayers(long pids[], boolean dropout[]) {
        List<TourneyMatch> updateMatches = new ArrayList<>();
        for (Iterator<TourneySection> sections = getSections().iterator(); sections.hasNext(); ) {
            TourneySection s = sections.next();
            updateMatches.addAll(s.forfeitPlayers(pids, dropout));
        }
        return updateMatches;
    }

    public void updateAlreadyPlayed(int alreadyPlayed[][]) {
        for (Iterator<TourneySection> sections = getSections().iterator(); sections.hasNext(); ) {
            TourneySection s = sections.next();
            s.updateAlreadyPlayed(alreadyPlayed);
        }
    }

    public List<TourneyPlayerData> getWinners() {
        List<TourneyPlayerData> winners = new ArrayList<>(5);
        for (Iterator<TourneySection> sections = getSections().iterator(); sections.hasNext(); ) {
            TourneySection s = sections.next();
            winners.addAll(s.getWinners());
        }
        return winners;
    }
}
