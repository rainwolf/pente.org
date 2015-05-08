package org.pente.gameServer.tourney;

import java.util.*;
import java.sql.*;

import org.pente.database.*;
import org.pente.game.*;

public class MySQLTourneyStorer implements TourneyStorer {

    public static void main(String args[]) throws Throwable {
        DBHandler dbHandler = new MySQLDBHandler(
            args[0], args[1], args[2], args[3]);

        MySQLTourneyStorer storer = new MySQLTourneyStorer(dbHandler, null);
        Tourney t = storer.getTourney(1116);
        //List ss = ((TourneyRound) t.getRounds().get(0)).getSections();
        //TourneySection s = ((TourneySection) ss.get(0));
        //int r[][] = s.getResultsMatrix();
        //System.out.println(r);
        
        //List players = storer.setInitialSeeds(1088);
        //TourneyRound r = RoundRobinFormat.createRound(players, 1088, 1);
        //TourneyRound r = t.createFirstRound(players, 1088);
        
//        if (t.getLastRound().isComplete()) {
//            TourneyRound r = t.createNextRound();
//            List l = r.getMatchStrings();
//        }
        
        //TourneyRound r2 = t.getRound(2);
        //r2.getMatchStrings();
        
        //RoundRobinSection rrs = (RoundRobinSection) t.getRound(1).getSection(6);
        //int rr[][] = rrs.getResultsMatrix();
        
        //System.out.println("complete=" + t.isComplete());
        
        //t.getLastRound().dropPlayers(new long[] {22000000004945L,
        //    23000000004047L, 23000000003311L });
        //t.getLastRound().getSection(1).init();
//        t.getLastRound().getSection(2).init();
//        List winners = t.getLastRound().getSection(1).getWinners();
//        winners = t.getLastRound().getSection(2).getWinners();
//        boolean complete = t.isComplete();
//        t.createNextRound();
        System.out.println("done");
    }
    
    private DBHandler dbHandler;
    private GameVenueStorer gameVenueStorer;
    
    public MySQLTourneyStorer(DBHandler dbHandler, GameVenueStorer gameVenueStorer) {
        this.dbHandler = dbHandler;
        this.gameVenueStorer = gameVenueStorer;
    }
    
    public List getUpcomingTournies() throws Throwable {
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;
        List signup = new ArrayList();

        try {
            con = dbHandler.getConnection();
            stmt = con.prepareStatement(
                "select e.eid, e.name " +
                "from game_event e, dsg_tournament_detail t " +
                "where e.eid = t.event_id " +
                "and t.signup_end_date > sysdate()");
            result = stmt.executeQuery(); 
            while (result.next()) {
                Tourney tourney = new Tourney(result.getInt(1));
                tourney.setName(result.getString(2));

                signup.add(tourney);
            }

        } finally {
            if (result != null) { 
                result.close();
            }
            if (stmt != null) {
                stmt.close();
            }
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
        
        return signup;
    }
    public List getCurrentTournies()
        throws Throwable {
        
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;
        List current = new ArrayList();

        try {
            con = dbHandler.getConnection();
            stmt = con.prepareStatement(
                "select e.eid, e.name " +
                "from game_event e, dsg_tournament_detail t " +
                "where e.eid = t.event_id " +
                "and sysdate() > t.signup_end_date " +
                "and t.completion_date is null");
            result = stmt.executeQuery(); 
            while (result.next()) {
                Tourney tourney = new Tourney(result.getInt(1));
                tourney.setName(result.getString(2));

                current.add(tourney);
            }

        } finally {
            if (result != null) { 
                result.close();
            }
            if (stmt != null) {
                stmt.close();
            }
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
        
        return current;
    }
    public List getCompletedTournies() throws Throwable {
        
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;
        List completed = new ArrayList();
    
        try {
            con = dbHandler.getConnection();
            stmt = con.prepareStatement(
                "select e.eid, e.name " +
                "from game_event e, dsg_tournament_detail t " +
                "where e.eid = t.event_id " +
                "and t.completion_date is not null " +
                "order by t.completion_date desc");
            result = stmt.executeQuery(); 
            while (result.next()) {
                Tourney tourney = new Tourney(result.getInt(1));
                tourney.setName(result.getString(2));
    
                completed.add(tourney);
            }
    
        } finally {
            if (result != null) { 
                result.close();
            }
            if (stmt != null) {
                stmt.close();
            }
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
        
        return completed;
    }

    public void completeTourney(Tourney tourney) throws Throwable {
        
        Connection con = null;
        PreparedStatement stmt = null;
        try {
            con = dbHandler.getConnection();
            stmt = con.prepareStatement(
                "update dsg_tournament_detail " +
                "set completion_date = ? " +
                "where event_id = ?");
            stmt.setTimestamp(1, new Timestamp(tourney.getEndDate().getTime()));
            stmt.setInt(2, tourney.getEventID());
            stmt.executeUpdate(); 
         
            stmt.close();
            
            int tw = -1;
            if (tourney.getPrize() != null) {
                if (tourney.getPrize().equals("gold")) tw = 1;
                else if (tourney.getPrize().equals("silver")) tw = 2;
            }
            if (tw != -1) {
                stmt  = con.prepareStatement(
                    "update dsg_player_game " +
                    "set tourney_winner = '0' " +
                    "where game = ? " +
                    "and tourney_winner = '" + tw + "'");
                stmt.setInt(1, tourney.getGame());
                stmt.executeUpdate();
                
                stmt.close();

                stmt  = con.prepareStatement(
                    "update dsg_player_game " +
                    "set tourney_winner = '" + tw + "' " +
                    "where game = ? " +
                    "and pid = ? " +
                    "and computer = 'N'");
                stmt.setInt(1, tourney.getGame());
                stmt.setLong(2, tourney.getWinnerPid());
                stmt.executeUpdate();
            }
            
        } finally {

            if (stmt != null) {
                stmt.close();
            }
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
    }

    public void insertTourney(Tourney tourney) throws Throwable {
        Connection con = null;
        PreparedStatement stmt = null;

        try {
            GameEventData newEvent = new SimpleGameEventData();
            newEvent.setGame(tourney.getGame());
            newEvent.setName(tourney.getName());
            gameVenueStorer.addGameEventData(tourney.getGame(), newEvent,
                DSG2_12GameFormat.SITE_NAME);
            tourney.setEventID(newEvent.getEventID());
            
            con = dbHandler.getConnection();
            stmt = con.prepareStatement(
                "insert into dsg_tournament_detail " +
                "(event_id, timer, initial_time, incremental_time, " +
                "round_length_days, creation_date, signup_end_date, start_date, " +
                "format, speed, prize) " +
                "values(?, 'I', ?, ?, ?, sysdate(), ?, ?, ?, ?, ?)");

            stmt.setInt(1, newEvent.getEventID());
            stmt.setInt(2, tourney.getInitialTime());
            stmt.setInt(3, tourney.getIncrementalTime());
            stmt.setInt(4, tourney.getRoundLengthDays());
            Timestamp t = new Timestamp(tourney.getSignupEndDate().getTime());
            stmt.setTimestamp(5, t);
            t = new Timestamp(tourney.getStartDate().getTime());
            stmt.setTimestamp(6, t);
            if (tourney.getFormat() instanceof RoundRobinFormat) {
                stmt.setInt(7, 1);
            }
            else if (tourney.getFormat() instanceof DoubleEliminationFormat) {
                stmt.setInt(7, 3);
            }
            else if (tourney.getFormat() instanceof SingleEliminationFormat) {
                stmt.setInt(7, 2);
            }
            else if (tourney.getFormat() instanceof SwissFormat) {
                stmt.setInt(7, 4);
            }
            stmt.setString(8, tourney.isSpeed() ? "Y" : "N");
            stmt.setString(9, tourney.getPrize());
            stmt.execute();
            stmt.close();
            
            stmt = con.prepareStatement(
                "insert into dsg_tournament_admin " +
                "(event_id, pid) " +
                "values(?, ?)");
            stmt.setInt(1, tourney.getEventID());
            for (Iterator it = tourney.getDirectors().iterator(); it.hasNext();) {
                stmt.setLong(2, ((Long) it.next()).longValue());
                stmt.execute();
            }
            
            stmt.close();
            
            stmt = con.prepareStatement(
                "insert into dsg_tournament_restriction " +
                "(event_id, type, value)" +
                "values(?, ?, ?)");
            stmt.setInt(1, tourney.getEventID());
            for (Restriction r : tourney.getRestrictions()) {
                stmt.setInt(2, r.getType());
                stmt.setInt(3, r.getValue());
                stmt.execute();
            }

        } finally {
            if (stmt != null) {
                stmt.close();
            }
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
    }

    public Tourney getTourney(int eid) 
        throws Throwable {
        
        Tourney tourney = getTourneyDetails(eid);
        if (tourney != null) {
            
            Connection con = null;
            PreparedStatement stmt = null;
            ResultSet results = null;
            Map players = new HashMap();
            try {
                con = dbHandler.getConnection();
                stmt = con.prepareStatement(
                    "select m.mid, m.round, m.section, m.gid, m.p1_pid, " +
                    "pl1.name, p1.seed, m.p2_pid, pl2.name, p2.seed, m.result, " +
                    "m.forfeit " +
                    "from dsg_tournament_match m " +
                    "left outer join dsg_tournament_player p1 on m.event_id = p1.event_id " +
                    "and p1.pid = m.p1_pid " +
                    "left outer join dsg_tournament_player p2 on m.event_id = p2.event_id " +
                    "and p2.pid = m.p2_pid " +
                    "left outer join player pl1 on pl1.pid = m.p1_pid " +
                    "left outer join player pl2 on pl2.pid = m.p2_pid " +
                    "where m.event_id = ? " +
                    "order by round, section, p1.seed, p2.seed");
                stmt.setInt(1, eid);
                
                // assumes always have at least one of each
                TourneyRound currentRound = new TourneyRound(1);
                TourneySection currentSection = tourney.createSection(1);

                results = stmt.executeQuery();
                while (results.next()) {
                    TourneyMatch match =  new TourneyMatch();
                    match.setEvent(eid);
                    match.setMatchID(results.getLong(1));
                    match.setRound(results.getInt(2));
                    match.setSection(results.getInt(3));
                    match.setGid(results.getLong(4));
                    
                    TourneyPlayerData player1 = (TourneyPlayerData) 
                        players.get(results.getString(6));
                    if (player1 == null) {
                        player1 = new TourneyPlayerData();
                        player1.setPlayerID(results.getLong(5));
                        player1.setName(results.getString(6));
                        player1.setSeed(results.getInt(7));
                        players.put(player1.getName(), player1);
                    }
                    match.setPlayer1(player1);
                    
                    // if not, it's a bye
                    if (results.getLong(8) > 0) {
                        TourneyPlayerData player2 = (TourneyPlayerData) 
                            players.get(results.getString(9));
                        if (player2 == null) {
                            player2 = new TourneyPlayerData();
                            player2.setPlayerID(results.getLong(8));
                            player2.setName(results.getString(9));
                            player2.setSeed(results.getInt(10));
                            players.put(player2.getName(), player2);
                        }
                        match.setPlayer2(player2);
                    }

                    match.setResult(results.getInt(11));
                    match.setForfeit(results.getString(12).equals("Y"));
                    
                    if (match.getRound() > currentRound.getRound()) {
                        // add current section/round to tourney, before creating new ones
                        if (!currentSection.isEmpty()) {
                            currentRound.addSection(currentSection);
                        }

                        tourney.addRound(currentRound);
                        
                        currentRound = new TourneyRound(match.getRound());
                        currentSection = tourney.createSection(1);
                    }
                    else if (match.getSection() > currentSection.getSection()) {

                        currentRound.addSection(currentSection);
                        currentSection = tourney.createSection(match.getSection());
                    }

                    currentSection.addMatch(match);
                }
                
                // make sure to add the final round/section to tourney
                if (!currentSection.isEmpty()) {
                    currentRound.addSection(currentSection);
                }
                if (!currentRound.isEmpty()) {
                    tourney.addRound(currentRound);
                }
            
                tourney.init();
                
            } finally {
                if (results != null) {
                    results.close();
                }
                if (stmt != null) {
                    stmt.close();
                }
                if (con != null) {
                    dbHandler.freeConnection(con);
                }
            }
        }
        
        return tourney;
    }
    
    public void addPlayerToTourney(long pid, int eid)
        throws Throwable {
        
        Connection con = null;
        PreparedStatement stmt = null;
        try {
            con = dbHandler.getConnection();
            stmt = con.prepareStatement(
                "insert into dsg_tournament_player " +
                "(event_id, pid, signup_date) " +
                "values(?, ?, sysdate())");
            stmt.setInt(1, eid);
            stmt.setLong(2, pid);
            stmt.executeUpdate(); 
            
        } finally {

            if (stmt != null) {
                stmt.close();
            }
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
    }
    public void removePlayerFromTourney(long pid, int eid) throws Throwable {
        Connection con = null;
        PreparedStatement stmt = null;
        try {
            con = dbHandler.getConnection();
            stmt = con.prepareStatement(
                "delete from dsg_tournament_player " +
                "where event_id = ? and pid = ?");
            stmt.setInt(1, eid);
            stmt.setLong(2, pid);
            stmt.executeUpdate(); 
            
        } finally {

            if (stmt != null) {
                stmt.close();
            }
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
    }

    /** get List of TourneyPlayerData for all players in a tourney */
    public List getTourneyPlayers(int eid)
        throws Throwable {

        List players = new ArrayList();
        
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet results = null;
        try {
            con = dbHandler.getConnection();
            stmt = con.prepareStatement(
                "select tp.pid, p.name, tp.seed, g.wins + g.losses, g.rating " +
                "from dsg_tournament_player tp left outer join dsg_player_game g " +
                "on tp.pid = g.pid join player p left outer join game_event e " +
                "on g.game = e.game and g.computer='N' " +
                "where tp.event_id = ? " +
                "and tp.event_id = e.eid " +
                "and tp.pid = p.pid " +
                "order by g.rating desc");
            stmt.setInt(1, eid);
            results = stmt.executeQuery(); 
            while (results.next()) {
                TourneyPlayerData t = new TourneyPlayerData();
                t.setPlayerID(results.getLong(1));
                t.setName(results.getString(2));
                t.setSeed(results.getInt(3));
                t.setTotalGames(results.getInt(4));
                t.setRating((int)Math.round(results.getDouble(5)));
                players.add(t);
            }
            
            Collections.sort(players, new Comparator() {
                public int compare(Object obj1, Object obj2) {
                    TourneyPlayerData t1 = (TourneyPlayerData) obj1;
                    TourneyPlayerData t2 = (TourneyPlayerData) obj2;

                    if (t1.getTotalGames() > 19 && t2.getTotalGames() < 20) {
                        return -1;
                    }
                    else if (t1.getTotalGames() < 20 && t2.getTotalGames() > 19) {
                        return 1;
                    }
                    else {
                        return (int) (t2.getRating() - t1.getRating());          
                    }
                }
            });
            
        } finally {

            if (stmt != null) {
                stmt.close();
            }
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
        return players;
    }

    public Tourney getTourneyDetails(int eid) throws Throwable {

        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet results = null;
        
        Tourney details = null;
        
        try {
            con = dbHandler.getConnection();
            stmt = con.prepareStatement(
                "select e.eid, e.name, e.game, t.signup_end_date, t.start_date, " +
                "t.initial_time, t.incremental_time, t.round_length_days, " +
                "t.format, t.speed, t.forumID, t.completion_date, t.prize " +
                "from game_event e, dsg_tournament_detail t " +
                "where e.eid=? " +
                "and e.eid = t.event_id");
            stmt.setInt(1, eid);
            
            results = stmt.executeQuery();
            if (results.next()) {
                details = new Tourney(results.getInt(1));
                details.setName(results.getString(2));
                details.setGame(results.getInt(3));
                Timestamp t = results.getTimestamp(4);
                details.setSignupEndDate(new java.util.Date(t.getTime()));
                t = results.getTimestamp(5);
                details.setStartDate(new java.util.Date(t.getTime()));
                details.setInitialTime(results.getInt(6));
                details.setIncrementalTime(results.getInt(7));
                details.setRoundLengthDays(results.getInt(8));
                
                int format = results.getInt(9);
                if (format == 1) {
                    details.setFormat(new RoundRobinFormat());
                }
                else if (format == 2) {
                    details.setFormat(new SingleEliminationFormat());
                }
                else if (format == 3) {
                    details.setFormat(new DoubleEliminationFormat());
                }
                else if (format == 4) {
                    details.setFormat(new SwissFormat());
                }
                details.setSpeed(results.getString(10).equals("Y"));
                details.setForumID(results.getLong(11));
                t = results.getTimestamp(12);
                if (t != null) {
                    details.setEndDate(new java.util.Date(t.getTime()));
                }
                details.setPrize(results.getString(13));
            }

            results.close();
            stmt.close();
            
            stmt = con.prepareStatement(
                "select pid " +
                "from dsg_tournament_admin " +
                "where event_id = ?");
            stmt.setInt(1, eid);
            results = stmt.executeQuery();
            while (results.next()) {
                details.addDirector(results.getLong(1));
            }

            results.close();
            stmt.close();
            
            stmt = con.prepareStatement(
                "select type, value " +
                "from dsg_tournament_restriction " +
                "where event_id = ?");
            stmt.setInt(1, eid);
            results = stmt.executeQuery();
            while (results.next()) {
                Restriction r = new Restriction(
                    results.getInt(1), results.getInt(2));
                details.addRestriction(r);
            }
            
        } finally {
            if (results != null) {
                results.close();
            }
            if (stmt != null) {
                stmt.close();
            }
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
        
        return details;
    }
    
    public TourneyMatch getUnplayedMatch(
        long player1ID, long player2ID, int eid) throws Throwable {
        
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet results = null;
        
        TourneyMatch match = null;
        
        try {
            con = dbHandler.getConnection();
            stmt = con.prepareStatement(
                "select m.mid, m.round, m.section " +
                "from dsg_tournament_match m " +
                "where m.event_id = ? " +
                "and m.p1_pid = ? " +
                "and m.p2_pid = ? " +
                "and result is null");
            stmt.setInt(1, eid);
            stmt.setLong(2, player1ID);
            stmt.setLong(3, player2ID);
            
            results = stmt.executeQuery();
            if (results.next()) {
                match = new TourneyMatch();
                match.setEvent(eid);
                // only get pids since thats all we need right now
                TourneyPlayerData p1 = new TourneyPlayerData();
                p1.setPlayerID(player1ID);
                match.setPlayer1(p1);
                TourneyPlayerData p2 = new TourneyPlayerData();
                p2.setPlayerID(player2ID);
                match.setPlayer2(p2);
                
                match.setMatchID(results.getLong(1));
                match.setRound(results.getInt(2));
                match.setSection(results.getInt(3));
            }

        } finally {
            if (results != null) {
                results.close();
            }
            if (stmt != null) {
                stmt.close();
            }
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
        
        return match;
    }

    /** based on sorted list of players, update seeds in db */
    public List setInitialSeeds(int eid)
        throws Throwable {

        Connection con = null;
        PreparedStatement stmt = null;
        List players = getTourneyPlayers(eid);
        try {
            con = dbHandler.getConnection();
            stmt = con.prepareStatement(
                "update dsg_tournament_player " +
                "set seed = ? " +
                "where event_id = ? " +
                "and pid = ?");
            for (int i = 0; i < players.size(); i++) {
                TourneyPlayerData p = (TourneyPlayerData) players.get(i);
                stmt.setInt(1, i + 1);
                stmt.setInt(2, eid);
                stmt.setLong(3, p.getPlayerID());
                stmt.executeUpdate();
                p.setSeed(i + 1);
            }

        } finally {

            if (stmt != null) {
                stmt.close();
            }
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
        return players;
    }

    /* shouldn't be actually used when Caching is used */
    public void insertRound(TourneyRound round)
        throws Throwable {
        
        for (Iterator sections = round.getSections().iterator(); sections.hasNext();) {
            TourneySection s = (TourneySection) sections.next();
            for (Iterator matches = s.getMatches().iterator(); matches.hasNext();) {
                TourneyMatch m = (TourneyMatch) matches.next();
                insertMatch(m);
            }
        }
    }

    public void insertMatch(TourneyMatch tourneyMatch) throws Throwable {

        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet result = null;
        try {
            con = dbHandler.getConnection();
            stmt = con.prepareStatement(
                "insert into dsg_tournament_match " +
                "(event_id, round, section, p1_pid, p2_pid, match_seq)" +
                "values(?, ?, ?, ?, ?, ?)");
            stmt.setInt(1, tourneyMatch.getEvent());
            stmt.setInt(2, tourneyMatch.getRound());
            stmt.setInt(3, tourneyMatch.getSection());
            stmt.setLong(4, tourneyMatch.getPlayer1().getPlayerID());
            if (tourneyMatch.isBye()) {
                stmt.setLong(5, 0);
            }
            else {
                stmt.setLong(5, tourneyMatch.getPlayer2().getPlayerID());
            }
            stmt.setInt(6, tourneyMatch.getSeq());
            stmt.executeUpdate();

            // get match_id and set it
            stmt.close();
            stmt = con.prepareStatement(
                "select max(mid) from dsg_tournament_match");
            result = stmt.executeQuery();
            if (result.next()) {
                tourneyMatch.setMatchID(result.getLong(1));
            }

        } finally {

            if (stmt != null) {
                stmt.close();
            }
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
    }

    public void updateMatch(TourneyMatch tourneyMatch) throws Throwable {
        
        Connection con = null;
        PreparedStatement stmt = null;
        try {
            con = dbHandler.getConnection();
            stmt = con.prepareStatement(
                "update dsg_tournament_match " +
                "set gid = ?, result = ?, forfeit = ? " +
                "where mid = ?");
            stmt.setLong(1, tourneyMatch.getGid());
            stmt.setInt(2, tourneyMatch.getResult());
            stmt.setString(3, tourneyMatch.isForfeit() ? "Y" : "N");
            stmt.setLong(4, tourneyMatch.getMatchID());
            stmt.executeUpdate(); 
            
        } finally {

            if (stmt != null) {
                stmt.close();
            }
            if (con != null) {
                dbHandler.freeConnection(con);
            }
        }
    }
    
    // not implemented here
    public void updateMatches(List tourneyMatches, Tourney t) throws Throwable {}
    public void addTourneyListener(TourneyListener listener) {}
    public void removeTourneyListener(TourneyListener listener) {}
}
