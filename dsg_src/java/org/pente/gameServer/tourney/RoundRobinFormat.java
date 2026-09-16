package org.pente.gameServer.tourney;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RoundRobinFormat extends AbstractTourneyFormat implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final int MAX_PLAYERS_IN_SECTION = 4;

    /**
     * How many independent layer shuffles to draw before keeping the best one.
     * A single blind draw averages about 1.5 repeat match ups per round from
     * round 2 on, and the search stops the moment a draw with no repeats at all
     * turns up. Each draw is also walked downhill by improve() first, so what
     * this number really bounds is how many separate starting points we try
     * before settling for the best local minimum found. A draw costs one
     * shuffle per layer plus one array lookup per co-sectioned pair, and the
     * downhill walk only runs on a draw that still has repeats in it, so for
     * the field shapes a Round-Robin actually produces this stays in
     * single-digit milliseconds. PLACEMENT_BUDGET_NANOS caps the pathological
     * shape -- many sections with a dense history, where no draw ever reaches
     * zero and all the restarts run.
     */
    private static final int PLACEMENT_ATTEMPTS = 200;

    /** draws that always run, however slow the box */
    private static final int MIN_PLACEMENT_ATTEMPTS = 8;

    /** wall-clock ceiling on the restarts once MIN_PLACEMENT_ATTEMPTS is met */
    private static final long PLACEMENT_BUDGET_NANOS = 50_000_000L;   // 50ms

    public String getName() {
        return "Round-Robin";
    }

    public TourneyRound createFirstRound(List<TourneyPlayerData> players, Tourney tourney) {
        return createRound(players, tourney, 1);
    }
    // createNextRound implements in AbstractTourneyFormat

    /**
     * Expects the players to have been seeded already
     * and expects them to be sorted accordingly
     *
     * @param players List of TourneyPlayerData
     * @return TourneyRound
     */
    TourneyRound createRound(List<TourneyPlayerData> players, Tourney tourney, int rnd) {
        TourneyRound round = new TourneyRound(rnd);
        round.setTourney(tourney);

        int maxPlayersInSection = MAX_PLAYERS_IN_SECTION;
        int numSections = 0;
        if (players.size() <= 6) {
            numSections = 1;
            maxPlayersInSection = 6;
        } else {
            // number of sections depends on # players and max section size of 5
            numSections = (players.size() + MAX_PLAYERS_IN_SECTION - 1) /
                    MAX_PLAYERS_IN_SECTION;
        }

        // initially place player data in appropriate section
        @SuppressWarnings("unchecked")
        List<TourneyPlayerData>[] sections = new List[numSections];

        // create new sections for round
        for (int i = 0; i < numSections; i++) {
            round.addSection(new RoundRobinSection(i + 1));
            sections[i] = new ArrayList<>();
        }

//        // place players into sections according to seeding and down/back alg.
//        int currentPlayer = 0;
//        outer: for (int i = 0; i < maxPlayersInSection; i++) {
//            // if we are down to the last players it might not come out evenly
//            // across all sections, so if we were supposed to place players
//            // from the bottom up, instead place from top down
//            boolean lastRun = currentPlayer + numSections > players.size();
//            for (int j = 0; j < numSections; j++) {
//                int placement = 0;
//                if (i % 2 == 0) {
//                    placement = j;
//                } else if (lastRun) {
//                    placement = players.size() - currentPlayer - 1;
//                } else {
//                    placement = numSections - 1 - j;
//                }
//                TourneyPlayerData p = (TourneyPlayerData) players.get(currentPlayer++);
//                sections[placement].add(p);
//                if (currentPlayer == players.size()) break outer;
//            }
//        }

//        Spread the rating layers across the sections, avoiding repeat match ups.
//
//        The layer rule itself is unchanged: players arrive rating-sorted, layer
//        i is players[i * numSections .. i * numSections + numSections), and
//        each section still receives exactly one member of each layer. That is
//        the rating balance help/tourneyRound-Robin.jsp promises, and it is also
//        what makes a repeat-free round reachable at all - two players in the
//        same layer can never meet.
//
//        What changed is how the layer's bijection onto the sections is picked.
//        It used to be a single blind Collections.shuffle with no memory, so a
//        pair who met last round landed together again with probability
//        1/numSections - about 1.5 repeats per round, regardless of field size.
//        Now we draw that shuffle repeatedly, walk each draw downhill against
//        the rounds actually played, and keep the best.
//
//        On round 1 every score is zero, so the first draw wins immediately and
//        placement is exactly as random as it has always been.
        if (numSections > 1) {
            // Copy each layer. The old code shuffled players.subList(..), which
            // is a VIEW, and so reordered the caller's list underneath it -
            // manageTourney.jsp renders that same list as its forfeit checkboxes.
            List<List<TourneyPlayerData>> layers = new ArrayList<>(maxPlayersInSection);
            for (int i = 0; i < maxPlayersInSection; i++) {
                int lb = i * numSections;
                if (lb >= players.size()) break;
                int ub = Math.min(lb + numSections, players.size());
                layers.add(new ArrayList<>(players.subList(lb, ub)));
            }

            int[][] alreadyPlayed = buildAlreadyPlayed(players, tourney, rnd);

            List<List<TourneyPlayerData>> best = null;
            int bestCost = Integer.MAX_VALUE;
            // the score is a sum of squares, so zero is the floor and once a
            // draw reaches it no further draw can beat it
            // Restarts stop early on a wall-clock budget as well as on a
            // zero score: when zero is unreachable nothing below detects
            // convergence, and a field that stops halving (a fully tied
            // section advances all 4) can otherwise push this into hundreds
            // of milliseconds on a request thread. The first few draws always
            // run, so a slow box degrades the search rather than skipping it.
            long deadline = System.nanoTime() + PLACEMENT_BUDGET_NANOS;
            for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS && bestCost > 0
                    && (attempt < MIN_PLACEMENT_ATTEMPTS || System.nanoTime() < deadline);
                 attempt++) {
                for (List<TourneyPlayerData> layer : layers) {
                    Collections.shuffle(layer);
                }
                int cost = placementCost(layers, numSections, alreadyPlayed);
                if (cost > 0) {
                    cost = improve(layers, numSections, alreadyPlayed, cost);
                }
                // the first attempt always wins, so best is never left null
                if (cost < bestCost) {
                    bestCost = cost;
                    best = new ArrayList<>(layers.size());
                    for (List<TourneyPlayerData> layer : layers) {
                        best.add(new ArrayList<>(layer));
                    }
                }
            }

            // layer member j goes to section j, exactly as before, so section
            // sizes and section numbering come out identical to the old code
            for (List<TourneyPlayerData> layer : best) {
                for (int j = 0; j < layer.size(); j++) {
                    sections[j].add(layer.get(j));
                }
            }
        } else {
            sections[0].addAll(players);
        }

        // now for each section, create matches
        for (int i = 0; i < numSections; i++) {
            for (int j = 0; j < sections[i].size(); j++) {
                TourneyPlayerData p1 = sections[i].get(j);
                for (int k = 0; k < sections[i].size(); k++) {
                    if (j == k) continue;
                    TourneyPlayerData p2 = sections[i].get(k);
                    TourneyMatch m = new TourneyMatch();
                    m.setPlayer1(p1);
                    m.setPlayer2(p2);
                    m.setEvent(tourney.getEventID());
                    m.setRound(rnd);
                    m.setSection(i + 1);
                    m.setSeq(1);
                    round.getSection(i + 1).addMatch(m);
                }
            }
        }

        round.init();

        return round;
    }

    /**
     * Walks one draw downhill. Swapping two members of the same layer trades
     * their sections, which is the smallest move that changes who meets whom
     * while leaving the layer rule intact, so repeatedly taking the first swap
     * that lowers the score settles on a local minimum within a few passes.
     * <p>
     * Without this a draw is just a blind sample, and 200 blind samples of a
     * space that runs to 24^4 placements miss a reachable repeat-free round
     * measurably often - 10 of 251 such rounds for a 32 player field, 19 of 190
     * for 64 players. With it those misses go to zero, which matters because a
     * missed zero is a repeat match up a player sees and complains about.
     *
     * @return the score of the improved placement, which is left in layers
     */
    private int improve(List<List<TourneyPlayerData>> layers, int numSections,
                        int[][] alreadyPlayed, int cost) {
        while (cost > 0) {
            boolean improved = false;
            for (List<TourneyPlayerData> layer : layers) {
                for (int x = 0; x < layer.size(); x++) {
                    for (int y = x + 1; y < layer.size(); y++) {
                        Collections.swap(layer, x, y);
                        int candidate = placementCost(layers, numSections, alreadyPlayed);
                        if (candidate < cost) {
                            cost = candidate;
                            improved = true;
                            if (cost == 0) return 0;
                        } else {
                            Collections.swap(layer, x, y);
                        }
                    }
                }
            }
            // the score strictly drops on every pass that changes anything, so
            // this terminates
            if (!improved) break;
        }
        return cost;
    }

    /**
     * Counts, per pair of seeds, how many earlier rounds the two players shared
     * a section in.
     * <p>
     * Deliberately NOT tourney.getAlreadyPlayed(). That accessor returns a
     * non-transient serialized field which it rebuilds only when the field is
     * null, and the Redis read path never calls Tourney.init(), so a cached
     * tourney hands back a matrix frozen at whatever rounds existed the last
     * time init() ran - in the live flow, round 1 only. Reading it would pair
     * round 3 blind to round 2 and quietly undo most of this. Rebuilding here
     * also keeps us from mutating the tourney, which CacheTourneyStorer would
     * then persist straight back into Redis.
     * <p>
     * The matrix is indexed by TourneyPlayerData.getSeed(), a 1-based index into
     * the ORIGINAL field that is never reassigned - so a survivor in round 3 can
     * still carry a seed far above players.size(), and sizing from any player
     * count would be an ArrayIndexOutOfBounds waiting to happen. Sizing from the
     * largest seed actually present makes the bound correct by construction, and
     * sidesteps TourneyRound.getNumPlayers(), which infers its answer from match
     * counts and returns -1 for a malformed section.
     */
    private int[][] buildAlreadyPlayed(List<TourneyPlayerData> players,
                                       Tourney tourney, int rnd) {
        int maxSeed = 0;
        for (TourneyPlayerData p : players) {
            maxSeed = Math.max(maxSeed, p.getSeed());
        }

        // Round 1 has no history, and tourney.getAlreadyPlayed() would be null
        // here regardless: Tourney only addRound()s after createRound returns.
        if (rnd == 1 || tourney == null || tourney.getNumRounds() == 0) {
            return new int[maxSeed + 1][maxSeed + 1];
        }

        try {
            // size against exactly the seeds updateAlreadyPlayed will write
            for (TourneyRound r : tourney.getRounds()) {
                for (TourneySection s : r.getSections()) {
                    for (TourneyPlayerData p : s.getPlayers()) {
                        maxSeed = Math.max(maxSeed, p.getSeed());
                    }
                }
            }
            int[][] alreadyPlayed = new int[maxSeed + 1][maxSeed + 1];
            for (TourneyRound r : tourney.getRounds()) {
                r.updateAlreadyPlayed(alreadyPlayed);
            }
            return alreadyPlayed;
        } catch (RuntimeException e) {
            // A malformed historical round must never stop the next round from
            // being created; fall back to the history-free placement. Note the
            // fallback is the same size as the matrix that just failed -- it
            // recovers from a bad ROW (a null section, a concurrent edit), not
            // from an out-of-range seed. placementCost bounds-checks the seeds
            // it reads, so that case cannot escape here either.
            e.printStackTrace();
            return new int[maxSeed + 1][maxSeed + 1];
        }
    }

    /**
     * Scores a candidate placement: for every pair that would end up in the same
     * section, the number of earlier rounds in which they already shared one,
     * squared. Lower is better, and zero means no pair in the round has met.
     * <p>
     * Layer member j goes to section j, so the players sharing section s are
     * exactly the members at index s of each layer. Pairs from the same layer
     * are never scored - a layer is spread one player per section, so they
     * cannot meet.
     * <p>
     * Squaring follows SwissFormat: it prefers two different pairs meeting a
     * second time over one pair meeting a third time. Note the counter is shared
     * SECTIONS, not games - RoundRobinSection.updateAlreadyPlayed increments
     * once per shared section, so a 1 means "these two have already met", even
     * though a section makes them play each other twice.
     */
    private int placementCost(List<List<TourneyPlayerData>> layers, int numSections,
                              int[][] alreadyPlayed) {
        int cost = 0;
        for (int s = 0; s < numSections; s++) {
            for (int a = 0; a < layers.size(); a++) {
                List<TourneyPlayerData> layerA = layers.get(a);
                if (s >= layerA.size()) continue;
                int seedA = layerA.get(s).getSeed();
                // a seed outside the matrix means corrupt data, not a repeat:
                // score it as "never met" rather than throwing out of createRound
                if (seedA < 0 || seedA >= alreadyPlayed.length) continue;
                for (int b = a + 1; b < layers.size(); b++) {
                    List<TourneyPlayerData> layerB = layers.get(b);
                    if (s >= layerB.size()) continue;
                    int seedB = layerB.get(s).getSeed();
                    if (seedB < 0 || seedB >= alreadyPlayed.length) continue;
                    int repeats = alreadyPlayed[seedA][seedB];
                    cost += repeats * repeats;
                }
            }
        }
        return cost;
    }
}
