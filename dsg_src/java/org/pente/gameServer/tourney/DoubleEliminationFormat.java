package org.pente.gameServer.tourney;

import java.util.*;

import org.apache.log4j.*;
import org.pente.game.GridStateFactory;

public class DoubleEliminationFormat extends SingleEliminationFormat {

    private Category log4j = Category.getInstance(
        DoubleEliminationFormat.class.getName());

    public String getName() {
        return "Double-Elimination";
    }

    // SingleEliminationFormat will handle first round the same
    //public TourneyRound createFirstRound(List players, int eid)

    private class PotentialMatch {
        public TourneyPlayerData p1;
        public TourneyPlayerData p2;
        public int distance;
        public PotentialMatch(TourneyPlayerData p1, TourneyPlayerData p2,
            int distance) {
            if (p1.getSeed() < p2.getSeed()) {
                this.p1 = p1;
                this.p2 = p2;
            }
            else {
                this.p2 = p1;
                this.p1 = p2;
            }
            this.distance = distance;
        }
        
        public void createRealMatch(int eid, int round, TourneySection s, boolean set) {

            newMatch(eid, round, s, p1, p2);

            if (set) {
                newMatch(eid, round, s, p2, p1);
            }
        }

        private void newMatch(int eid, int round, TourneySection s, TourneyPlayerData p1, TourneyPlayerData p2) {
            TourneyMatch m1 = new TourneyMatch();
            m1.setPlayer1(p1);
            m1.setPlayer2(p2);
            m1.setEvent(eid);
            m1.setRound(round);
            m1.setSection(s.getSection());
            m1.setSeq(1);
            s.addMatch(m1);
        }
    }
    private class PotentialSection {
        public List<PotentialMatch> matches = new ArrayList<PotentialMatch>(10);
        public int numRepeats;
        public int distFromOpt;
        public PotentialSection() {}//empty section
        public PotentialSection(PotentialMatch m, int repeat) {
            addMatch(m, repeat);
        }
        public void addMatch(PotentialMatch m, int repeat) {
            matches.add(m);
            distFromOpt += m.distance * m.distance;
            numRepeats += repeat * repeat;
        }
        public boolean isOptimal() {
            return numRepeats == 0 && distFromOpt == 0;
        }
        public boolean isBetterThan(PotentialSection pr2) {
            if (pr2.numRepeats > numRepeats) return true;
            else if (pr2.numRepeats < numRepeats) return false;
            else if (pr2.distFromOpt >= distFromOpt) return true;
            else return false;
        }
        
        public TourneySection createRealSection(int eid, int round, int section, boolean set) {
            TourneySection s = new SingleEliminationSection(section);
            Collections.sort(matches, new Comparator<PotentialMatch>() {
                public int compare(PotentialMatch o1, PotentialMatch o2) {
                    PotentialMatch m1 = (PotentialMatch) o1;
                    PotentialMatch m2 = (PotentialMatch) o2;
                    return m1.p1.getSeed() - m2.p1.getSeed();
                }
            });
            for (Iterator it = matches.iterator(); it.hasNext();) {
                PotentialMatch m = (PotentialMatch) it.next();
                m.createRealMatch(eid, round, s, set);
            }
            return s;
        }
    }
    
    
    public int getSectionCount;
    private PotentialSection getSection(List<TourneyPlayerData> players, int alreadyPlayed[][]) {
        getSectionCount++;
        if (players.size() == 0) { // can occur after dropping players from bracket 2
            return new PotentialSection();
        }
        if (players.size() == 2) {
            TourneyPlayerData p1 = (TourneyPlayerData) players.get(0);
            TourneyPlayerData p2 = (TourneyPlayerData) players.get(1);
            log4j.info("getSection(), size=" + players.size() + ", " + p1.getName() + "-" + p2.getName());
            int repeat = alreadyPlayed[p1.getSeed()][p2.getSeed()];
            //p2.getName() + " r=" + repeat);
            return new PotentialSection(new PotentialMatch(p1, p2, 0), repeat);
        }
        
        PotentialSection ps = null;
        for (int j = players.size() - 1; j > 0; j--) {
            TourneyPlayerData p1 = (TourneyPlayerData) players.get(0);
            TourneyPlayerData p2 = (TourneyPlayerData) players.get(j);
            int repeat = alreadyPlayed[p1.getSeed()][p2.getSeed()];
            //log4j.debug("getSection(), size=" + players.size() + ", " + p1.getName() + "-" +
            //p2.getName() + " r=" + repeat);
            List<TourneyPlayerData> copy = new ArrayList<TourneyPlayerData>(players);
            copy.remove(p1);
            copy.remove(p2);
            PotentialSection sub = getSection(copy, alreadyPlayed);

            sub.addMatch(new PotentialMatch(p1, p2, players.size() - 1 - j), repeat);
            if (sub.isOptimal()) return sub;//shortcut
            else if (ps == null || sub.isBetterThan(ps)) ps = sub;
        }

        return ps;
    }

    @Override
    /** overridden because tourney can't tell by looking at just last round
     *  and looking at winner count for sections.  have to look at who has
     *  less than 2 losses
     */
    public boolean isTourneyComplete(Tourney tourney) {
        if (tourney.getNumRounds() == 0) return false;
        updatePlayerData(tourney);
        
        int livePlayers = 0;
        for (Iterator it = tourney.getLastRound().getSections().iterator(); it.hasNext();) {
            TourneySection s = (TourneySection) it.next();
            for (Iterator it2 = s.getPlayers().iterator(); it2.hasNext();) {
                TourneyPlayerData p = (TourneyPlayerData) it2.next();
                if (p.getMatchLosses() < 2) {
                    livePlayers++;
                }
            }
        }
        return livePlayers == 1;
    }
    
    /** updates data such as number of byes in whole tourney, number of match losses */
    void updatePlayerData(Tourney tourney) {
        if (tourney.getNumRounds() == 0) return;
        for (Iterator it = tourney.getRound(1).getPlayers().iterator(); it.hasNext();) {
            TourneyPlayerData p = (TourneyPlayerData) it.next();
            p.reset();
        }

        for (Iterator it = tourney.getRounds().iterator(); it.hasNext();) {
            TourneyRound r = (TourneyRound) it.next();
            for (Iterator it2 = r.getSections().iterator(); it2.hasNext();) {
                SingleEliminationSection s = (SingleEliminationSection) it2.next();
                for (Iterator it3 = s.getSingleEliminationMatches().iterator(); it3.hasNext();) {
                    SingleEliminationMatch m = (SingleEliminationMatch) it3.next();
                    if (m.isBye()) {
                        m.getPlayer1().incrementByes();
                        if (m.isForfeit()) { // not sure why this would happen but...
                            m.getPlayer1().incrementMatchLosses();
                        }
                    }
                    else if (m.getResult() == TourneyMatch.RESULT_DBL_FORFEIT) {
                        m.getPlayer1().incrementMatchLosses();
                        m.getPlayer2().incrementMatchLosses();
                    }
                    else if (m.getResult() == 1) {
                        m.getPlayer2().incrementMatchLosses();
                    }
                    else if (m.getResult() == 2) {
                        m.getPlayer1().incrementMatchLosses();
                    }
                }
            }
        }
    }

    public TourneyRound createNextRound(Tourney tourney) {
        
        int rnd = tourney.getNumRounds() + 1;
        TourneyRound round = new TourneyRound(rnd);
        TourneyRound lastRound = tourney.getLastRound();

        List<TourneyPlayerData> bracketPlayers0 = new ArrayList<TourneyPlayerData>();
        List<TourneyPlayerData> bracketPlayers1 = new ArrayList<TourneyPlayerData>();
        TourneySection brackets[] = new SingleEliminationSection[2];

        updatePlayerData(tourney);

        // add all players who don't have 2 losses to next round
        for (Iterator it = lastRound.getSections().iterator(); it.hasNext();) {
            TourneySection s = (TourneySection) it.next();
            for (Iterator it2 = s.getPlayers().iterator(); it2.hasNext();) {
                TourneyPlayerData p = (TourneyPlayerData) it2.next();

                // don't include players who drop out
                if (s.dropoutPlayers.contains(p.getPlayerID())) {
                    continue;
                }
                
                if (p.getMatchLosses() == 0) {
                    bracketPlayers0.add(p);
                }
                else if (p.getMatchLosses() == 1) {
                    bracketPlayers1.add(p);
                }
            }

        }
        // we've reached point where we're back down to 1 bracket
        if (bracketPlayers0.size() < 2) {
            bracketPlayers0.addAll(bracketPlayers1);
            bracketPlayers1.clear();
        }
            
        Comparator<TourneyPlayerData> seedComparator = new Comparator<TourneyPlayerData>() {
            public int compare(TourneyPlayerData o1, TourneyPlayerData o2) {
                TourneyPlayerData p1 = (TourneyPlayerData) o1;
                TourneyPlayerData p2 = (TourneyPlayerData) o2;
                return p1.getSeed() - p2.getSeed();
            }
        };
        boolean set = (tourney.getGame() != GridStateFactory.GO &&
                tourney.getGame() != GridStateFactory.GO9 &&
                tourney.getGame() != GridStateFactory.GO13 &&
                tourney.getGame() != GridStateFactory.SPEED_GO &&
                tourney.getGame() != GridStateFactory.SPEED_GO9 &&
                tourney.getGame() != GridStateFactory.SPEED_GO13 &&
                tourney.getGame() != GridStateFactory.TB_GO &&
                tourney.getGame() != GridStateFactory.TB_GO9 &&
                tourney.getGame() != GridStateFactory.TB_GO13);

            TourneyMatch byeMatch = null;
            if (!bracketPlayers0.isEmpty()) {
                // sort players in both brackets by seeds
                Collections.sort(bracketPlayers0, seedComparator);

                // uneven players, highest seed without a bye yet gets it
                if (bracketPlayers0.size() % 2 == 1) {
                    
                    List<TourneyPlayerData> byePlayers = new ArrayList<TourneyPlayerData>(bracketPlayers0);
                    Collections.sort(byePlayers, byeComparator);

                    byeMatch = new TourneyMatch();
                    byeMatch.setEvent(tourney.getEventID());
                    byeMatch.setRound(rnd);
                    byeMatch.setSection(1);
                    TourneyPlayerData byePlayer = (TourneyPlayerData) 
                        byePlayers.get(0);
                    byeMatch.setPlayer1(byePlayer);
                    bracketPlayers0.remove(byePlayer);
                    //log4j.debug("bye player=" + byePlayer.getName());
                }
                
                PotentialSection ps = getSection(bracketPlayers0, tourney.getAlreadyPlayed());
                TourneySection rs = ps.createRealSection(tourney.getEventID(), 
                    round.getRound(), 1, set);
                if (byeMatch != null) {
                    rs.addMatch(byeMatch);
                }

                rs.init();
                round.addSection(rs);
            } // no bracket 2
            byeMatch = null;
            if (!bracketPlayers1.isEmpty()) {
                // sort players in both brackets by seeds
                Collections.sort(bracketPlayers1, seedComparator);

                // uneven players, highest seed without a bye yet gets it
                if (bracketPlayers1.size() % 2 == 1) {
                    
                    List<TourneyPlayerData> byePlayers = new ArrayList<TourneyPlayerData>(bracketPlayers1);
                    Collections.sort(byePlayers, byeComparator);

                    byeMatch = new TourneyMatch();
                    byeMatch.setEvent(tourney.getEventID());
                    byeMatch.setRound(rnd);
                    byeMatch.setSection(2);
                    TourneyPlayerData byePlayer = (TourneyPlayerData) 
                        byePlayers.get(0);
                    byeMatch.setPlayer1(byePlayer);
                    bracketPlayers1.remove(byePlayer);
                    //log4j.debug("bye player=" + byePlayer.getName());
                }
                
                PotentialSection ps = getSection(bracketPlayers1, tourney.getAlreadyPlayed());
                TourneySection rs = ps.createRealSection(tourney.getEventID(), 
                    round.getRound(), 2, set);
                if (byeMatch != null) {
                    rs.addMatch(byeMatch);
                }

                rs.init();
                round.addSection(rs);
            } // no bracket 2

        
        return round;
    }

    public TourneyMatch[] createMoreMatchesAfterTie(TourneyMatch original) {
        TourneyMatch more[] = new TourneyMatch[2];
        for (int i = 0; i < 2; i++) {
            more[i] = new TourneyMatch();
            more[i].setEvent(original.getEvent());
            more[i].setRound(original.getRound());
            more[i].setSection(original.getSection());
            more[i].setSeq(original.getSeq() + 1);
        }
        more[0].setPlayer1(original.getPlayer1());
        more[0].setPlayer2(original.getPlayer2());
        // then swap
        more[1].setPlayer1(original.getPlayer2());
        more[1].setPlayer2(original.getPlayer1());
        
        return more;
    }
}
