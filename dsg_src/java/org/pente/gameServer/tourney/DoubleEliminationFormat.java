package org.pente.gameServer.tourney;

import java.util.*;

import org.apache.log4j.*;

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
        }
    }
    private class PotentialSection {
        public List matches = new ArrayList(10);
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
        
        public TourneySection createRealSection(int eid, int round, int section) {
            TourneySection s = new SingleEliminationSection(section);
            Collections.sort(matches, new Comparator() {
                public int compare(Object o1, Object o2) {
                    PotentialMatch m1 = (PotentialMatch) o1;
                    PotentialMatch m2 = (PotentialMatch) o2;
                    return m1.p1.getSeed() - m2.p1.getSeed();
                }
            });
            for (Iterator it = matches.iterator(); it.hasNext();) {
                PotentialMatch m = (PotentialMatch) it.next();
                m.createRealMatch(eid, round, s);
            }
            return s;
        }
    }
    
    
    public int getSectionCount;
    private PotentialSection getSection(List players, int alreadyPlayed[][]) {
        getSectionCount++;
        if (players.size() == 0) { // can occur after dropping players from bracket 2
            return new PotentialSection();
        }
        if (players.size() == 2) {
            TourneyPlayerData p1 = (TourneyPlayerData) players.get(0);
            TourneyPlayerData p2 = (TourneyPlayerData) players.get(1);
            int repeat = alreadyPlayed[p1.getSeed()][p2.getSeed()];
            //log4j.debug("getSection(), size=" + players.size() + ", " + p1.getName() + "-" +
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
            List copy = new ArrayList(players);
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

        List bracketPlayers[] = new List[2];
        bracketPlayers[0] = new ArrayList();
        bracketPlayers[1] = new ArrayList();
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
                    bracketPlayers[0].add(p);
                }
                else if (p.getMatchLosses() == 1) {
                    bracketPlayers[1].add(p);
                }
            }

        }
        // we've reached point where we're back down to 1 bracket
        if (bracketPlayers[0].size() < 2) {
            bracketPlayers[0].addAll(bracketPlayers[1]);
            bracketPlayers[1].clear();
        }
            
        Comparator seedComparator = new Comparator() {
            public int compare(Object o1, Object o2) {
                TourneyPlayerData p1 = (TourneyPlayerData) o1;
                TourneyPlayerData p2 = (TourneyPlayerData) o2;
                return p1.getSeed() - p2.getSeed();
            }
        };

        for (int i = 0; i < 2; i++) {
            TourneyMatch byeMatch = null;
            if (bracketPlayers[i].isEmpty()) break; // no bracket 2

	        // sort players in both brackets by seeds
            Collections.sort(bracketPlayers[i], seedComparator);

            // uneven players, highest seed without a bye yet gets it
            if (bracketPlayers[i].size() % 2 == 1) {
                
                List byePlayers = new ArrayList(bracketPlayers[i]);
                Collections.sort(byePlayers, byeComparator);

                byeMatch = new TourneyMatch();
                byeMatch.setEvent(tourney.getEventID());
                byeMatch.setRound(rnd);
                byeMatch.setSection(i + 1);
                TourneyPlayerData byePlayer = (TourneyPlayerData) 
                    byePlayers.get(0);
                byeMatch.setPlayer1(byePlayer);
                bracketPlayers[i].remove(byePlayer);
                //log4j.debug("bye player=" + byePlayer.getName());
            }
            
            PotentialSection ps = getSection(bracketPlayers[i], tourney.getAlreadyPlayed());
            TourneySection rs = ps.createRealSection(tourney.getEventID(), 
                round.getRound(), i + 1);
            if (byeMatch != null) {
                rs.addMatch(byeMatch);
            }

            rs.init();
            round.addSection(rs);
        }
        
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
