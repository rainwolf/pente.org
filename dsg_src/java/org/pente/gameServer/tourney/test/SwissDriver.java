package org.pente.gameServer.tourney.test;

import java.util.*;

import org.pente.database.*;
import org.pente.gameServer.tourney.*;

import org.apache.log4j.*;


public class SwissDriver {

    private static Category log4j = Category.getInstance(
            SwissDriver.class.getName());

    public static void main(String[] args) throws Throwable {

        BasicConfigurator.configure();

        DBHandler dbHandler = new MySQLDBHandler(
                args[0], args[1], args[2], args[3]);

        MySQLTourneyStorer storer = new MySQLTourneyStorer(dbHandler, null);
        CacheTourneyStorer tourneyStorer = new CacheTourneyStorer(storer);
//        
        Tourney t = storer.getTourney(1142);
        for (Iterator it = ((SingleEliminationSection) t.getRound(1).
                getSection(1)).getSingleEliminationMatches().iterator(); it.hasNext(); ) {

            SingleEliminationMatch m = (SingleEliminationMatch) it.next();
            System.out.println(m.getPlayer1().getName() + " " +
                    m.getResultStr() + " " + m.getPlayer2().getName());
        }

    }
/*
        SwissSection ss = (SwissSection) t.getLastRound().getSection(1);
		ss.getPlayersRanked(t);
		
        //TourneyRound r = t.createNextRound();
        //tourneyStorer.insertRound(r);
        //boolean complete = t.getLastRound().isComplete();
        List p = ((SwissSection) t.getRound(2).getSection(1)).getPlayersRanked(t);
        
        List players = storer.setInitialSeeds(1089);
        TourneyRound r = t.createFirstRound(players);
        tourneyStorer.insertRound(r);
        
//        CacheTourneyStorer tourneyStorer = new CacheTourneyStorer(
//                new DummyTourneyStorer());


//        Tourney t = new Tourney();
//        t.setEventID(1);
//        t.setFormat(new SwissFormat());
//
//        tourneyStorer.insertTourney(t);
//        List players = createPlayers(6);
//        TourneyRound r = t.createFirstRound(players);
        
        List matchups = r.getMatchStrings();
        for (Iterator strs = matchups.iterator(); strs.hasNext();) {
            log4j.info((String) strs.next());
        }

        long startTime = System.currentTimeMillis();
        
        do {
            // complete all matches, using somewhat random determination
            for (Iterator it = r.getSections().iterator(); it.hasNext();) {
                SwissSection s = (SwissSection) it.next();
                for (int i = 0; i < s.getNumTotalMatches(); i++) {
                    TourneyMatch m = (TourneyMatch) s.getMatches().get(i);
                    if (m == null) {
                        boolean f =false;
                    }
                    if (m.getPlayer2() == null) continue;//skip bye
                    double rDiff = m.getPlayer1().getRating() - m.getPlayer2().getRating();
                    // for every 50 ratings points diff, add an extra 5% chance of winning
                    double rMult = rDiff / 50 * .05;
                    if (rMult > 1) rMult=1;
                    else if (rMult < -1) rMult=-1;
                    // bigger ratings diff, means less chance for p2 to win
                    double rand = Math.random();
                    int result = (int) (rand - rMult + 1.5);
                    if (result < 1) {
                        result = 1;
                    } else if (result > 2) {
                        result = 2;
                    }
                    m.setResult(result);
                    tourneyStorer.updateMatch(m);
                }
            }
            int rnum = t.getLastRound().getRound() - 1;
            r = t.getRound(rnum);
            //log4j.info("round " + t.getNumRounds() + " complete");
            matchups = r.getMatchStrings();
            for (Iterator strs = matchups.iterator(); strs.hasNext();) {
                log4j.info((String) strs.next());
            }
            
            if (t.isComplete() || t.getNumRounds() == 5) break;
            
            //log4j.info("create round "+ (t.getNumRounds() + 1));
            //r = t.createNextRound();
            r = t.getLastRound();
            matchups = r.getMatchStrings();
            for (Iterator strs = matchups.iterator(); strs.hasNext();) {
                log4j.info((String) strs.next());
            }
            
        } while (true);
        
        log4j.info("tourney complete, winner=" + t.getWinner());
        log4j.info("total time = " + (System.currentTimeMillis() - startTime));
        SwissFormat f = (SwissFormat)
            t.getFormat();
        log4j.info("called getSection " + f.getSectionCount + " times.");

    }


    private static List createPlayers(int numPlayers) {
        List players = new ArrayList(numPlayers);
        for (int i = 0; i < numPlayers; i++) {
            TourneyPlayerData p = new TourneyPlayerData();
            p.setName("player" + i);
            p.setPlayerID(1L + i);
            int rand = (int) (Math.random() * 200);
            p.setRating(1200 + i*20 + rand);
            p.setTotalGames(21);
            players.add(p);
        }

        Collections.shuffle(players);
        
        for (int i = 0; i < numPlayers; i++) {
            TourneyPlayerData p = (TourneyPlayerData) players.get(i);
            p.setSeed(i + 1);
            p.setName("p" + p.getSeed());
        }

        return players;
    }
    */
}    

