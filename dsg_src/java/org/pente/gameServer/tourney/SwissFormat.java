package org.pente.gameServer.tourney;

import org.pente.gameServer.core.DSGPlayerStorer;

import java.util.*;

public class SwissFormat implements TourneyFormat {

    public String getName() {
        return "Swiss";
    }

    public boolean isTourneyComplete(Tourney tourney) {
        if (tourney.getEndDate() != null) return true;

        if (tourney.getNumRounds() == 0) return false;
        for (Iterator<TourneyRound> rounds = tourney.getRounds().iterator(); rounds.hasNext(); ) {
            TourneyRound r = (TourneyRound) rounds.next();
            if (!r.isComplete()) return false;
        }

        int numberOfPlayers = tourney.getLastRound().getNumPlayers();
        int numberOfRounds = tourney.getNumRounds();
        if (numberOfPlayers < 16) {
            return numberOfRounds > 3;
        }
        if (numberOfPlayers < 32) {
            return numberOfRounds > 4;
        }
        if (numberOfPlayers < 64) {
            return numberOfRounds > 5;
        }
        return numberOfRounds > 6;
    }

    public TourneyRound createFirstRound(List<TourneyPlayerData> players, Tourney tourney) {
        Collections.shuffle(players);

        return createRound(players, tourney, 1);
    }

    public TourneyRound createNextRound(Tourney tourney, DSGPlayerStorer dsgPlayerStorer) {
        SwissSection s = (SwissSection) tourney.getLastRound().getSection(1);
        List<TourneyPlayerData> players = s.getPlayersRanked(tourney);
        return createRound(players, tourney, tourney.getNumRounds() + 1);
    }

    private TourneyRound createRound(List<TourneyPlayerData> players, Tourney tourney, int rnd) {

        TourneyRound round = new TourneyRound(rnd);

        int alreadyPlayed[][] = rnd == 1 ?
                new int[players.size() + 1][players.size() + 1] :
                tourney.getAlreadyPlayed();

        TourneyMatch byeMatch = null;
        if (players.size() % 2 == 1) {
            List<TourneyPlayerData> byePlayers = new ArrayList<>(players);
            if (rnd > 1) {
                // sort players by byes ascending
                // then wins ascending, player who gets the bye is 1st in list
                Collections.sort(byePlayers, (o1, o2) -> {
                    TourneyPlayerData p1 = (TourneyPlayerData) o1;
                    TourneyPlayerData p2 = (TourneyPlayerData) o2;
                    if (p1.getNumByes() > p2.getNumByes()) return 1;
                    else if (p1.getNumByes() < p2.getNumByes()) return -1;
                    else return p1.getMatchWins() - p2.getMatchWins();
                });
            }
            byeMatch = new TourneyMatch();
            byeMatch.setEvent(tourney.getEventID());
            byeMatch.setRound(rnd);
            byeMatch.setSection(1);
            byeMatch.setPlayer1((TourneyPlayerData) byePlayers.getFirst());
            players.remove(byePlayers.getFirst());
        }

        PotentialSection ps = getSection(players, alreadyPlayed);
        TourneySection rs = ps.createRealSection(tourney.getEventID(),
                round.getRound(), 1);
        if (byeMatch != null) {
            rs.addMatch(byeMatch);
        }

        rs.init();
        round.addSection(rs);

        return round;
    }

    private class PotentialMatch {
        public TourneyPlayerData p1;
        public TourneyPlayerData p2;
        public int distance;

        public PotentialMatch(TourneyPlayerData p1, TourneyPlayerData p2,
                              int distance) {
            if (p1.getSeed() < p2.getSeed()) {
                this.p1 = p1;
                this.p2 = p2;
            } else {
                this.p2 = p1;
                this.p1 = p2;
            }
            this.distance = distance;
        }

        public void createRealMatch(int eid, int round, TourneySection s) {

            TourneyMatch m1 = new TourneyMatch();
            m1.setPlayer1(p1);
            m1.setPlayer2(p2);
            m1.setEvent(eid);
            m1.setRound(round);
            m1.setSection(s.getSection());
            m1.setSeq(1);
            s.addMatch(m1);

            TourneyMatch m2 = new TourneyMatch();
            m2.setPlayer1(p2);
            m2.setPlayer2(p1);
            m2.setEvent(eid);
            m2.setRound(round);
            m2.setSection(s.getSection());
            m2.setSeq(1);
            s.addMatch(m2);

            TourneyMatch m3 = new TourneyMatch();
            m3.setPlayer1(p1);
            m3.setPlayer2(p2);
            m3.setEvent(eid);
            m3.setRound(round);
            m3.setSection(s.getSection());
            m3.setSeq(1);
            s.addMatch(m3);

            TourneyMatch m4 = new TourneyMatch();
            m4.setPlayer1(p2);
            m4.setPlayer2(p1);
            m4.setEvent(eid);
            m4.setRound(round);
            m4.setSection(s.getSection());
            m4.setSeq(1);
            s.addMatch(m4);
        }
    }

    private class PotentialSection {
        public List<PotentialMatch> matches = new ArrayList<>(10);
        public int numRepeats;
        public int distFromOpt;

        public PotentialSection() {
        }//empty section

        public PotentialSection(PotentialMatch m, int repeat) {
            addMatch(m, repeat);
        }

        public void addMatch(PotentialMatch m, int repeat) {
            matches.add(m);
            distFromOpt += m.distance;
            numRepeats += repeat * repeat;
        }

        public boolean isOptimal() {
            return numRepeats == 0 && distFromOpt < 10000;
        }

        public boolean isBetterThan(PotentialSection pr2) {
            if (pr2.numRepeats > numRepeats) return true;
            else if (pr2.numRepeats < numRepeats) return false;
            else if (pr2.distFromOpt >= distFromOpt) return true;
            else return false;
        }

        public TourneySection createRealSection(int eid, int round, int section) {
            TourneySection s = new SwissSection(section);
            Collections.sort(matches, (o1, o2) -> {
                PotentialMatch m1 = (PotentialMatch) o1;
                PotentialMatch m2 = (PotentialMatch) o2;
                return m1.p1.getSeed() - m2.p1.getSeed();
            });
            for (Iterator<PotentialMatch> it = matches.iterator(); it.hasNext(); ) {
                PotentialMatch m = (PotentialMatch) it.next();
                m.createRealMatch(eid, round, s);
            }
            return s;
        }
    }


    public int getSectionCount;

    private PotentialSection getSection(List<TourneyPlayerData> players, int alreadyPlayed[][]) {
        getSectionCount++;
        if (players.size() == 2) {
            TourneyPlayerData p1 = (TourneyPlayerData) players.get(0);
            TourneyPlayerData p2 = (TourneyPlayerData) players.get(1);
            int repeat = alreadyPlayed[p1.getSeed()][p2.getSeed()];
            //log4j.debug("getSection(), size=" + players.size() + ", " + p1.getName() + "-" +
            //p2.getName() + " r=" + repeat);
            return new PotentialSection(new PotentialMatch(p1, p2, 0), repeat);
        }

        PotentialSection ps = null;
        for (int j = 1; j < 4 && j < players.size(); j++) {
            TourneyPlayerData p1 = (TourneyPlayerData) players.get(0);
            TourneyPlayerData p2 = (TourneyPlayerData) players.get(j);
            int repeat = alreadyPlayed[p1.getSeed()][p2.getSeed()];
            //log4j.debug("getSection(), size=" + players.size() + ", " + p1.getName() + "-" +
            //p2.getName() + " r=" + repeat);
            List<TourneyPlayerData> copy = new ArrayList<>(players);
            copy.remove(p1);
            copy.remove(p2);
            PotentialSection sub = getSection(copy, alreadyPlayed);

            int distance = (j - 1) * (j - 1) * 10000;
            distance += Math.abs(p1.getMatchWins() - p2.getMatchWins()) * 1000;
            distance += Math.abs(Math.abs(p1.getOpponentWins() - p2.getOpponentWins()));

            sub.addMatch(new PotentialMatch(p1, p2, distance), repeat);
            if (sub.isOptimal()) return sub;//shortcut
            else if (ps == null || sub.isBetterThan(ps)) ps = sub;
        }

        return ps;
    }
}
