package org.pente.game;

import java.util.*;

import static org.pente.game.ZobristUtil.rand;

/**
 * Created by waliedothman on 16/05/2017.
 */
public class GoState extends GridStateDecorator
        implements GridState, HashCalculator {

    private boolean allowSuicide = false;

    private boolean allowRepetition = false;
    public boolean isSuicideAllowed() { return allowSuicide; }
    public void setAllowSuicide(boolean allowSuicide) { this.allowSuicide = allowSuicide; }
    public boolean isRepetitionAllowed() { return allowRepetition; }
    public void setAllowRepetition(boolean allowRepetition) { this.allowRepetition = allowRepetition; }

    protected Map<Integer,Map<Integer, List<Integer>>> groupsByPlayerAndID;
    protected Map<Integer,Map<Integer, Integer>> stoneGroupIDsByPlayer;


    protected int koMove;
    public int getKoMove() { return koMove; }

    public int capturedAt[][];
    public int capturedMoves[][];
    public int captures[];

    protected int passMove;
    protected int handicapPass;


    protected boolean markStones = false;
    protected boolean evaluateStones = false;
    public boolean isMarkStones() { return markStones; }
    public boolean isEvaluateStones() { return evaluateStones; }
    
    protected List<Long> positionHashes;
    public List<Long> getPositionHashes() { return positionHashes; }

    protected List<Integer> deadStones;
    public List<Integer> getDeadStones() { return deadStones; }
    
    HashMap<Integer, List<Integer>> deadStonesByPlayer;

    private Map<Integer, List<Integer>> goTerritoryByPlayer;
    
    public SimpleGridState getGridState() { return (SimpleGridState) this.gridState; } 

    public GoState(GridState gridState) {
        super(gridState);
        ((SimpleGridState) this.gridState).setAllowNonBoardMoves(true);
        init();
    }

    public GoState(int boardSizeX, int boardSizeY) {
        super(boardSizeX, boardSizeY);
        ((SimpleGridState) this.gridState).setAllowNonBoardMoves(true);
        init();
    }

    public GridState getInstance(MoveData moveData) {
        GoState goState = new GoState(gridState.getGridSizeX(), gridState.getGridSizeY());

        for (int i = 0; i < moveData.getNumMoves(); i++) {
            goState.addMove(moveData.getMove(i));
        }

        return goState;
    }

    protected void init() {
        this.groupsByPlayerAndID = new HashMap<>();
        this.groupsByPlayerAndID.put(1, new HashMap<>());
        this.groupsByPlayerAndID.put(2, new HashMap<>());
        this.stoneGroupIDsByPlayer = new HashMap<>();
        this.stoneGroupIDsByPlayer.put(1, new HashMap<>());
        this.stoneGroupIDsByPlayer.put(2, new HashMap<>());
        this.koMove = -1;
        captures = new int[3];
        capturedAt = new int[3][361];
        capturedMoves = new int[3][361];
        
        passMove = getGridSizeX() * getGridSizeY();
        handicapPass = passMove + 1;
        
        positionHashes = new ArrayList<>();
        positionHashes.add(0L);
        
        deadStones = new ArrayList<>();
        deadStonesByPlayer = new HashMap<>();
        deadStonesByPlayer.put(1, new ArrayList<>());
        deadStonesByPlayer.put(2, new ArrayList<>());
        
        
        hasPass = false;
        markStones = false;
        evaluateStones = false;
    }

    
    private boolean hasPass;
    public synchronized void addMove(int move) {

        if (evaluateStones) {
            if (move == passMove) {
                addDeadStone(move);
            }
        } else if (markStones) {
            ((SimpleGridState)gridState).setAllowOccupiedMoves(true);
            if (move == passMove) {
                evaluateStones = true;
            }
            addDeadStone(move);
        } else {

            int currentPlayer = getCurrentPlayer();

            ((SimpleGridState)gridState).setAllowOccupiedMoves(false);
            gridState.addMove(move);

            if (move == passMove) {
                if (hasPass) {
                    markStones = true;
                } else {
                    hasPass = true;
                }
            } else {
                hasPass = false;
            }
            if (0 <= move && move < passMove) {
                positionHashes.add(positionHashes.get(positionHashes.size()-1) ^ rand[currentPlayer-1][move]);

                Map<Integer, List<Integer>> groupsByID = getGroupsByPlayerAndID().get(currentPlayer);
                Map<Integer, Integer> stoneGroupIDs = getStoneGroupIDsByPlayer().get(currentPlayer);
                settleGroups(move, groupsByID, stoneGroupIDs);

                int opponent = 3 - currentPlayer;
                groupsByID = getGroupsByPlayerAndID().get(opponent);
                stoneGroupIDs = getStoneGroupIDsByPlayer().get(opponent);
                makeCaptures(move, groupsByID, stoneGroupIDs);

                if (isSuicideAllowed()) {
                    groupsByID = getGroupsByPlayerAndID().get(currentPlayer);
                    stoneGroupIDs = getStoneGroupIDsByPlayer().get(currentPlayer);
                    int moveGroupID = stoneGroupIDs.get(move);
                    List<Integer> moveGroup = groupsByID.get(moveGroupID);
                    if (!groupHasLiberties(moveGroup)) {
                        captureGroup(moveGroupID, groupsByID, stoneGroupIDs);
                    }
                }
            }

        }

        updateHash(this);
        

        //        printBoard();
    }
    
    
    
    private synchronized void makeCaptures(int move, Map<Integer, List<Integer>> groupsByID, Map<Integer, Integer> stoneGroupIDs) {
        int captures = 0;
        if (move%getGridSizeX() != 0) {
            int neighborStone = move - 1;
            Integer neighborStoneGroupID = stoneGroupIDs.get(neighborStone);
            captures = getCaptures(move, groupsByID, stoneGroupIDs, captures, neighborStone, neighborStoneGroupID);
        }
        if (move%getGridSizeX() != getGridSizeX() - 1) {
            int neighborStone = move + 1;
            Integer neighborStoneGroupID = stoneGroupIDs.get(neighborStone);
            captures = getCaptures(move, groupsByID, stoneGroupIDs, captures, neighborStone, neighborStoneGroupID);
        }
        if (move/getGridSizeX() != 0) {
            int neighborStone = move - getGridSizeX();
            Integer neighborStoneGroupID = stoneGroupIDs.get(neighborStone);
            captures = getCaptures(move, groupsByID, stoneGroupIDs, captures, neighborStone, neighborStoneGroupID);
        }
        if (move/getGridSizeX() != getGridSizeX() - 1) {
            int neighborStone = move + getGridSizeX();
            Integer neighborStoneGroupID = stoneGroupIDs.get(neighborStone);
            captures = getCaptures(move, groupsByID, stoneGroupIDs, captures, neighborStone, neighborStoneGroupID);
        }
        if (captures != 1) {
            koMove = -1;
        }
    }

    private synchronized int getCaptures(int move, Map<Integer, List<Integer>> groupsByID, Map<Integer, Integer> stoneGroupIDs, int captures, int neighborStone, Integer neighborStoneGroupID) {
        if (neighborStoneGroupID != null) {
            List<Integer> neighborStoneGroup = groupsByID.get(neighborStoneGroupID);
            if (!groupHasLiberties(neighborStoneGroup)) {
                if (koMove < 0 && neighborStoneGroup.size() == 1 && checkKo(move)) {
                    koMove = neighborStone;
                } else {
                    koMove = -1;
                }
                captures += neighborStoneGroup.size();
                captureGroup(neighborStoneGroupID, groupsByID, stoneGroupIDs);
            }
        }
        return captures;
    }

    private synchronized boolean checkKo(int move) {
        int position = getPosition(move);
        if (move%getGridSizeX() != 0) {
            int neighborStone = move - 1;
            int neighborPosition = getPosition(neighborStone);
            if (position != 3 - neighborPosition) {
                return false;
            }
        }
        if (move%getGridSizeX() != getGridSizeX() - 1) {
            int neighborStone = move + 1;
            int neighborPosition = getPosition(neighborStone);
            if (position != 3 - neighborPosition) {
                return false;
            }
        }
        if (move/getGridSizeX() != 0) {
            int neighborStone = move - getGridSizeX();
            int neighborPosition = getPosition(neighborStone);
            if (position != 3 - neighborPosition) {
                return false;
            }
        }
        if (move/getGridSizeX() != getGridSizeX() - 1) {
            int neighborStone = move + getGridSizeX();
            int neighborPosition = getPosition(neighborStone);
            if (position != 3 - neighborPosition) {
                return false;
            }
        }
        return true;
    }


    private synchronized void captureGroup(int groupID, Map<Integer, List<Integer>> groupsByID, Map<Integer, Integer> stoneGroupIDs) {
        List<Integer> group = groupsByID.get(groupID);
        int capturer = 0;
        if (group.size() > 0) {
            capturer = 3 - getPosition(group.get(0));
        }
        for (int stone: group) {
            captureMove(stone, capturer);
//            setPosition(stone, 0);
            stoneGroupIDs.remove(stone);
        }
        groupsByID.remove(groupID);
    }

    protected synchronized boolean groupHasLiberties(List<Integer> group) {
        for (int stone: group) {
            if (stoneHasLiberties(stone)) {
                return true;
            }
        }
        return false;
    }
    private synchronized boolean stoneHasLiberties(int stone) {
        if (stone%getGridSizeX() != 0) {
            int neighborStone = stone - 1;
            int position = getPosition(neighborStone);
            if (position != 1 && position != 2) {
                return true;
            }
        }
        if (stone%getGridSizeX() != getGridSizeX() - 1) {
            int neighborStone = stone + 1;
            int position = getPosition(neighborStone);
            if (position != 1 && position != 2) {
                return true;
            }
        }
        if (stone/getGridSizeX() != 0) {
            int neighborStone = stone - getGridSizeX();
            int position = getPosition(neighborStone);
            if (position != 1 && position != 2) {
                return true;
            }
        }
        if (stone/getGridSizeX() != getGridSizeX() - 1) {
            int neighborStone = stone + getGridSizeX();
            int position = getPosition(neighborStone);
            if (position != 1 && position != 2) {
                return true;
            }
        }
        return false;
    }
    
    private synchronized void settleGroups(int move, Map<Integer, List<Integer>> groupsByID, Map<Integer, Integer> stoneGroupIDs) {
        List<Integer> newGroup = new ArrayList<>();
        newGroup.add(move);
        groupsByID.put(move, newGroup);
        stoneGroupIDs.put(move, move);
        if (move%getGridSizeX() != 0) {
            int neighborStone = move - 1;
            Integer neighborStoneGroupID = stoneGroupIDs.get(neighborStone);
            if (neighborStoneGroupID != null) {
                mergeGroups(move, neighborStoneGroupID, groupsByID, stoneGroupIDs);
            }
        }
        if (move%getGridSizeX() != getGridSizeX() - 1) {
            int neighborStone = move + 1;
            Integer neighborStoneGroupID = stoneGroupIDs.get(neighborStone);
            if (neighborStoneGroupID != null) {
                mergeGroups(stoneGroupIDs.get(move), neighborStoneGroupID, groupsByID, stoneGroupIDs);
            }
        }
        if (move/getGridSizeX() != 0) {
            int neighborStone = move - getGridSizeX();
            Integer neighborStoneGroupID = stoneGroupIDs.get(neighborStone);
            if (neighborStoneGroupID != null) {
                mergeGroups(stoneGroupIDs.get(move), neighborStoneGroupID, groupsByID, stoneGroupIDs);
            }
        }
        if (move/getGridSizeX() != getGridSizeX() - 1) {
            int neighborStone = move + getGridSizeX();
            Integer neighborStoneGroupID = stoneGroupIDs.get(neighborStone);
            if (neighborStoneGroupID != null) {
                mergeGroups(stoneGroupIDs.get(move), neighborStoneGroupID, groupsByID, stoneGroupIDs);
            }
        }
    }
    
    private synchronized void mergeGroups(int group1, int group2, Map<Integer, List<Integer>> groupsByID, Map<Integer, Integer> stoneGroupIDs) {
        if (group1 == group2) {
            return;
        }
        List<Integer> oldGroup, newGroup;
        int oldGroupID, newGroupID;
        if (group1 < group2) {
            oldGroup = groupsByID.get(group1);
            newGroup = groupsByID.get(group2);
            oldGroupID = group1;
            newGroupID = group2;
        } else {
            oldGroup = groupsByID.get(group2);
            newGroup = groupsByID.get(group1);
            oldGroupID = group2;
            newGroupID = group1;
        }
        newGroup.addAll(oldGroup);
        groupsByID.remove(oldGroupID);
        for (int oldStone: oldGroup) {
            stoneGroupIDs.replace(oldStone, newGroupID);
        }
    }

    private List<Integer> getGroupWithoutMerge(int move, Map<Integer, List<Integer>> groupsByID, Map<Integer, Integer> stoneGroupIDs) {
        List<Integer> newGroup = new ArrayList<>();
        newGroup.add(move);
        if (move%getGridSizeX() != 0) {
            int neighborStone = move - 1;
            Integer neighborStoneGroupID = stoneGroupIDs.get(neighborStone);
            if (neighborStoneGroupID != null) {
                newGroup.addAll(groupsByID.get(neighborStoneGroupID));
            }
        }
        if (move%getGridSizeX() != getGridSizeX() - 1) {
            int neighborStone = move + 1;
            Integer neighborStoneGroupID = stoneGroupIDs.get(neighborStone);
            if (neighborStoneGroupID != null) {
                newGroup.addAll(groupsByID.get(neighborStoneGroupID));
            }
        }
        if (move/getGridSizeX() != 0) {
            int neighborStone = move - getGridSizeX();
            Integer neighborStoneGroupID = stoneGroupIDs.get(neighborStone);
            if (neighborStoneGroupID != null) {
                newGroup.addAll(groupsByID.get(neighborStoneGroupID));
            }
        }
        if (move/getGridSizeX() != getGridSizeX() - 1) {
            int neighborStone = move + getGridSizeX();
            Integer neighborStoneGroupID = stoneGroupIDs.get(neighborStone);
            if (neighborStoneGroupID != null) {
                newGroup.addAll(groupsByID.get(neighborStoneGroupID));
            }
        }
        return newGroup;
    }

    public boolean canPlayerUndo(int player) {
        if (markStones && !evaluateStones) {
            return getMove(getNumMoves() - 1) != passMove && player == getCurrentPlayer();
        }
        return getNumMoves() > 0 && player != getCurrentPlayer();
    }
    
    public synchronized boolean isValidMove(int move, int player) {
        
//        System.out.println("isValidMove " + player + " " + move);

        if (player != getCurrentPlayer()) {
//            System.out.println("isValidMove getCurrentPlayer " + getCurrentPlayer() + " player = " + player);
            return false;
        }

        if (move == passMove) {
            return true;
        }
        
        if (evaluateStones) {
            return false;
        }

//        if (move == handicapPass && getNumMoves() < 18) {
//            return true;
//        }

        try {
            checkOutOfBounds(move);
        } catch (IllegalArgumentException e) {
            return false;
        }

        if (markStones) {
            if (getPosition(move) != 0) {
                return true;
            } else {
                return false;
            }
        }

        if (getPosition(move) != 0) {
//            System.out.println("isValidMove getPosition");
            return false;
        }

        if (move == koMove) {
//            System.out.println("ko move "+koMove);
            return false;
        }

        boolean suicide = false, repetition = false;


        if (!isSuicideAllowed() || !isRepetitionAllowed()) {
            addMove(move);

            if (!isSuicideAllowed() && !groupHasLiberties(groupsByPlayerAndID.get(player).get(stoneGroupIDsByPlayer.get(player).get(move)))) {
                undoMove();
//                System.out.println("isValidMove isSuicideAllowed");
                return false;
            }

//       check for Super-Ko
            if (!isRepetitionAllowed()) {
                if (positionHashes.indexOf(positionHashes.get(positionHashes.size()-1)) < positionHashes.size()-1) {
                    undoMove();
//                    System.out.println("isValidMove isRepetitionAllowed");
                    return false;
                }
            }
            undoMove();
        }


        return true;
    }

    public synchronized int getCurrentPlayer() {
        int cp = 0;
        int dp = containsDoublePass();
        if (evaluateStones) {
            cp = dp % 2 + 1;
        } else if (markStones) {
            cp = 2 - dp % 2;
        } else {
            cp = gridState.getCurrentPlayer();
        }
        return cp;
    }
    
    public void clear() {
        gridState.clear();
        init();
    }



    public synchronized void checkOutOfBounds(int move) {
        Coord p = convertMove(move);
        checkOutOfBounds(p.x, p.y);
    }
    public synchronized void checkOutOfBounds(int x, int y) {
        if (x < 0 || x >= getGridSizeX() || y < 0 || y >= getGridSizeY()) {
            throw new IllegalArgumentException("Out of bounds");
        }
    }

    public synchronized boolean doublePass() {
        Vector<Integer> moves = ((SimpleGridState)gridState).getMovesVector();
        return moves != null &&
                moves.size() > 1 &&
                moves.get(moves.size() - 1) == passMove &&
                moves.get(moves.size() - 2) == passMove;
    }
    protected synchronized int containsDoublePass() {
        boolean hasPass = false;
        Vector<Integer> moves = ((SimpleGridState)gridState).getMovesVector();
        for (int i = 0; i < moves.size(); i++) {
            int move = moves.get(i);
            if (move == passMove) {
                if (hasPass) {
                    return i;
                } else {
                    hasPass = true;
                }
            } else {
                hasPass = false;
            }
        }
        return -1;
    }
    
    
    public synchronized boolean isGameOver() {
        int numMoves = gridState.getNumMoves();
        if (containsDoublePass() < numMoves - 1 && deadStones != null && deadStones.size() > 1 
                && deadStones.get(deadStones.size() - 1) == passMove
                && deadStones.get(deadStones.size() - 2) == passMove) {
            return true;
        }         
        return false;
    }
    
    public synchronized int getWinner() {
        for (int dead: deadStones) {
            if (dead < passMove) {
                setPosition(dead, 0);
            }
        }
        getTerritories();
        List<Integer> p1Territory = goTerritoryByPlayer.get(1), p2Territory = goTerritoryByPlayer.get(2);
        int p1Count = p1Territory.size();
        int p2Count = p2Territory.size();
        for (int i = 0; i < getGridSizeX(); i++) {
            for (int j = 0; j < getGridSizeY(); j++) {
                if (getPosition(i,j) == 1) {
                    p1Count += 1;
                } else if (getPosition(i,j) == 2) {
                    p2Count += 1;
                }
            }
        }
//        System.out.println(" p1 count ============ " + p1Count);
//        System.out.println(" p2 count ============ " + p2Count);
        if (p1Count > p2Count + 7) {
            return 1;
        } else {
            return 2;
        }
    }
    
    public String getScoreMessage() {
        List<Integer> p1Territory = goTerritoryByPlayer.get(1), p2Territory = goTerritoryByPlayer.get(2);
        int p1Count = p1Territory.size();
        int p2Count = p2Territory.size();
        for (int i = 0; i < getGridSizeX(); i++) {
            for (int j = 0; j < getGridSizeY(); j++) {
                if (getPosition(i,j) == 1) {
                    p1Count += 1;
                } else if (getPosition(i,j) == 2) {
                    p2Count += 1;
                }
            }
        }
        return "P1 score is "+p1Count+", and P2 score is "+(p2Count+7)+".5";
    }
   

    @Override
    public long calcHash(long cHash, int p, int move, int rot) {
        if (p == 0) {
            p = 3 - getCurrentPlayer(); // since we already moved
        }
        
        if (move >= getGridSizeX()*getGridSizeY()) {
            cHash ^= rand[p-1][move];
        } else {
            cHash ^= rand[p-1][rotateMove(move, rot)];
        }

        int op = 3 - p;

        // if was a capture, XOR out hash for captured pieces
        for (int i = 0; i < capturedAt[p].length; i++) {
            if (capturedAt[p][i] == 0){
                break;
            }
            else if (capturedAt[p][i] == getNumMoves() - 1) {
                int capMove = rotateMove(capturedMoves[p][i], rot);
                cHash ^= rand[op-1][capMove];
            }
        }

        // now XOR in number captures for each player
        if (captures[1] > 0) {
            cHash ^= rand[2][captures[1]];
        }
        if (captures[2] > 0) {
            cHash ^= rand[3][captures[2]];
        }

        return cHash;
    }

    @Override
    public void printBoard() {
        ((SimpleGridState) gridState).printBoard();
    }

    public Map<Integer, Map<Integer, List<Integer>>> getGroupsByPlayerAndID() { return groupsByPlayerAndID; }
    public void setGroupsByPlayerAndID(Map<Integer, Map<Integer, List<Integer>>> groupsByPlayerAndID) { this.groupsByPlayerAndID = groupsByPlayerAndID; }
    public Map<Integer, Map<Integer, Integer>> getStoneGroupIDsByPlayer() { return stoneGroupIDsByPlayer; }
    public void setStoneGroupIDsByPlayer(Map<Integer, Map<Integer, Integer>> stoneGroupIDsByPlayer) { this.stoneGroupIDsByPlayer = stoneGroupIDsByPlayer; }
    public HashMap<Integer, List<Integer>> getDeadStonesByPlayer() { return deadStonesByPlayer; }
    public void setDeadStonesByPlayer(HashMap<Integer, List<Integer>> deadStonesByPlayer) { this.deadStonesByPlayer = deadStonesByPlayer; }

    protected synchronized void captureMove(int move, int capturePlayer) {
        setPosition(move, 0);
        capturedAt[capturePlayer][this.captures[capturePlayer]] =
                gridState.getNumMoves() - 1;
        capturedMoves[capturePlayer][this.captures[capturePlayer]] = move;
        this.captures[capturePlayer]++;
        Long last = positionHashes.get(positionHashes.size()-1);
        positionHashes.remove(positionHashes.size()-1);
        positionHashes.add(last ^ rand[3-capturePlayer-1][move]);
    }
    public synchronized void undoMove() {
        Vector<Integer> oldMoves = new Vector<Integer>(((SimpleGridState)gridState).getMovesVector());
        oldMoves.remove(oldMoves.size()-1);
        gridState.clear();

        init();
        for (int move: oldMoves) {
            addMove(move);
        }
    }
    public synchronized void rejectAndContinue() {
        Vector<Integer> oldMoves = new Vector<>();
        int l = containsDoublePass() - 1;
        for (int i = 0; i < l; i++) {
            oldMoves.add((Integer) ((SimpleGridState)gridState).getMovesVector().get(i));
        }
        
        gridState.clear();

        init();
        for (int move: oldMoves) {
            addMove(move);
        }
    }
    
    private synchronized int getEmptyNeighbour(int move) {
        if (move%getGridSizeX() != 0) {
            int neighborStone = move - 1;
            if (getPosition(neighborStone) == 0) {
                return neighborStone;
            }
        }
        if (move%getGridSizeX() != getGridSizeX() - 1) {
            int neighborStone = move + 1;
            if (getPosition(neighborStone) == 0) {
                return neighborStone;
            }
        }
        if (move/getGridSizeX() != 0) {
            int neighborStone = move - getGridSizeX();
            if (getPosition(neighborStone) == 0) {
                return neighborStone;
            }
        }
        if (move/getGridSizeX() != getGridSizeX() - 1) {
            int neighborStone = move + getGridSizeY();
            if (getPosition(neighborStone) == 0) {
                return neighborStone;
            }
        }
        return -1;
    }
    private synchronized void floodFillWorker(int move, int value) {
        setPosition(move, value);
        int neighbourStone = getEmptyNeighbour(move);
        while (neighbourStone != -1) {
            floodFillWorker(neighbourStone, value);
            neighbourStone = getEmptyNeighbour(move);
        }
    }
    private synchronized List<Integer> floodFill(int player) {
        for (int move = 0; move < passMove; move++) {
            int pos = getPosition(move);
            if (pos == player) {
                int neighbourStone = getEmptyNeighbour(move);
                while (neighbourStone != -1) {
                    floodFillWorker(neighbourStone, player + 2);
                    neighbourStone = getEmptyNeighbour(move);
                }
            }
        }
        List<Integer> floodedTerritory = new ArrayList<>();
        for (int i = 0; i < passMove; i++) {
            int val = getPosition(i);
            if (val == player + 2) {
                floodedTerritory.add(i);
            }
        }
        return floodedTerritory;
    }
    private synchronized void resetGoBeforeFlood() {
        for (int i = 0; i < getGridSizeX(); i++ ) {
            for (int j = 0; j < getGridSizeY(); j++ ) {
                int pos = getPosition(i,j);
                if (pos != 1 && pos != 2) {
                    setPosition(i,j,0);
                }
            }
        }
    }
    private synchronized List<Integer> getMovesForValue(int val) {
        List<Integer> results = new ArrayList<>();
        for (int j = 0; j < getGridSizeY(); j++) {
            for (int i = 0; i < getGridSizeX(); i++) {
                if (getPosition(i,j) == val) {
                    results.add(j*19+i);
                }
            }
        }
        return results;
    }
    public synchronized Map<Integer, List<Integer>> getTerritories() {
        goTerritoryByPlayer = new HashMap<>();
        floodFill(1);
        List<Integer> p1Territory = getMovesForValue(3);
        resetGoBeforeFlood();
        floodFill(2);
        List<Integer> p2Territory = getMovesForValue(4);
        resetGoBeforeFlood();
        
        int i = p1Territory.size()-1, j = p2Territory.size()-1;
        
        while (i>-1 && j>-1) {
            int p1Stone = p1Territory.get(i), p2Stone = p2Territory.get(j);
            if (p1Stone == p2Stone) {
                p1Territory.remove(i);
                p2Territory.remove(j);
                --i;
                --j;
            } else if (p1Stone>p2Stone) {
                --i;
            } else {
                --j;
            }
        }
        
        goTerritoryByPlayer.put(1, p1Territory);
        goTerritoryByPlayer.put(2, p2Territory);
        return goTerritoryByPlayer;
    }
    
    protected void addDeadStone(int deadStone) {
        gridState.addMove(deadStone);
        deadStones.add(deadStone);
        if (deadStone < passMove) {
            int player = getPosition(deadStone);
            if (player == 1 || player == 2) {
                deadStonesByPlayer.get(player).add(deadStone);
                setPosition(deadStone, 0);
            }
        }
    }
    

}
