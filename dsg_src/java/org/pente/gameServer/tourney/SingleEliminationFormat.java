package org.pente.gameServer.tourney;

import java.util.*;

import org.pente.turnBased.*;
import org.pente.game.GridStateFactory;

public class SingleEliminationFormat extends AbstractTourneyFormat {

    public String getName() {
        return "Single-Elimination";
    }

    /** does seed-based matchup
     *  provides bye to highest seed who hasn't already received a bye in earlier
     *  rounds
     * 
     *  impossible to match up players again in later rounds since is single
     *  elimination
     */
    public TourneyRound createFirstRound(List players, Tourney tourney) {
        return createRound(players, tourney, 1);
    }
    // createNextRound implements in AbstractTourneyFormat

    /** updates number of byes in whole tourney */
    void updatePlayerData(Tourney tourney) {
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
                    }
                }
            }
        }
    }

    Comparator byeComparator = new Comparator() {
        public int compare(Object o1, Object o2) {
            TourneyPlayerData p1 = (TourneyPlayerData) o1;
            TourneyPlayerData p2 = (TourneyPlayerData) o2;
            if (p1.getNumByes() > p2.getNumByes()) return 1;
            else if (p1.getNumByes() < p2.getNumByes()) return -1;
            else return p1.getSeed() - p2.getSeed();
        }
    };
    
    TourneyRound createRound(List players, Tourney tourney, int rnd) {

        TourneyRound round = new TourneyRound(rnd);
        TourneySection section = new SingleEliminationSection(1); //everyone always in section 1
        round.addSection(section);
        
        int currentPlayer = 0;

        // get bye info
        if (rnd > 1) {
            updatePlayerData(tourney);
        }
        
        // uneven players, highest seed without a bye yet gets it
        if (players.size() % 2 == 1) {
            // first round we know seed1 gets the bye
            List byePlayers = new ArrayList(players);
            if (rnd > 1) {
                // sort players by byes ascending
                // then seed ascending, player who gets the bye is 1st in list
                Collections.sort(byePlayers, byeComparator);
            }

            TourneyMatch m = new TourneyMatch();
            m.setEvent(tourney.getEventID());
            m.setRound(rnd);
            m.setSection(1);
            m.setPlayer1((TourneyPlayerData) byePlayers.get(0));
            section.addMatch(m);
            players.remove(byePlayers.get(0));
        }

        int high = players.size() - 1;
        int low = 0;
        int matches = players.size() / 2;
        for (int i = 0; i < matches; i++) {
            TourneyMatch m = new TourneyMatch();
            m.setEvent(tourney.getEventID());
            m.setRound(rnd);
            m.setSection(1);
            TourneyPlayerData p1 = (TourneyPlayerData) players.get(low++);
            TourneyPlayerData p2 = (TourneyPlayerData) players.get(high--);
            m.setPlayer1(p1);
            m.setPlayer2(p2);
            m.setSeq(1);
            section.addMatch(m);
            
            // now add match with players swapped
            m = new TourneyMatch();
            m.setEvent(tourney.getEventID());
            m.setRound(rnd);
            m.setSection(1);
            m.setPlayer1(p2);
            m.setPlayer2(p1);
            m.setSeq(1);
            section.addMatch(m);
            
            if (GridStateFactory.isTurnbasedGame(tourney.getGame())) {
                
                TBGame g1 = new TBGame();
                g1.setGame(tourney.getGame());
                g1.setEventId(tourney.getEventID());
                g1.setDaysPerMove(tourney.getIncrementalTime());
                g1.setRated(true);
                g1.setRound(rnd);
                g1.setSection(1);
                g1.setPlayer1Pid(p1.getPlayerID());
                g1.setPlayer2Pid(p2.getPlayerID());
                g1.setStartDate(new Date());
                g1.setState(TBGame.STATE_ACTIVE);
                g1.addMove(180);

                TBGame g2 = new TBGame();
                g2.setGame(tourney.getGame());
                g2.setEventId(tourney.getEventID());
                g2.setDaysPerMove(tourney.getIncrementalTime());
                g2.setRated(true);
                g2.setRound(rnd);
                g2.setSection(1);
                g2.setPlayer1Pid(p2.getPlayerID());
                g2.setPlayer2Pid(p1.getPlayerID());
                g2.setStartDate(new Date());
                g2.setState(TBGame.STATE_ACTIVE);
                g2.addMove(180);


                TBSet set = new TBSet(g1, g2);
                set.setCreationDate(new Date());
                set.setState(TBSet.STATE_ACTIVE);
                set.setPlayer1Pid(g1.getPlayer1Pid());
                set.setPlayer2Pid(g2.getPlayer2Pid());

                section.addSet(set);
            }
        }
        
        section.init();
        
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
