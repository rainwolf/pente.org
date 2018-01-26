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

    private Map<Integer,Map<Integer, List<Integer>>> groupsByPlayerAndID;
    private Map<Integer,Map<Integer, Integer>> stoneGroupIDsByPlayer;
    
    private int koMove;

    private int capturedAt[][];
    private int capturedMoves[][];
    private int captures[];

    private int passMove;
    private int handicapPass;

    private List<Long> positionHashes;
    private List<Integer> deadStones;
    
    private Map<Integer, List<Integer>> goTerritoryByPlayer;
    

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

    private void init() {
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
    }


    public void addMove(int move) {

        if (containsDoublePass() > -1) {
            
            deadStones.add(move);
            ((SimpleGridState)gridState).setAllowOccupiedMoves(true);
            gridState.addMove(move);
            
        } else {
            
            int currentPlayer = getCurrentPlayer();

            ((SimpleGridState)gridState).setAllowOccupiedMoves(false);
            gridState.addMove(move);

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
    
    
    
    private void makeCaptures(int move, Map<Integer, List<Integer>> groupsByID, Map<Integer, Integer> stoneGroupIDs) {
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
//        this.captures[capturer] += captures;
    }

    private int getCaptures(int move, Map<Integer, List<Integer>> groupsByID, Map<Integer, Integer> stoneGroupIDs, int captures, int neighborStone, Integer neighborStoneGroupID) {
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

    private boolean checkKo(int move) {
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


    private void captureGroup(int groupID, Map<Integer, List<Integer>> groupsByID, Map<Integer, Integer> stoneGroupIDs) {
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

    private boolean groupHasLiberties(List<Integer> group) {
        for (int stone: group) {
            if (stoneHasLiberties(stone)) {
                return true;
            }
        }
        return false;
    }
    private boolean stoneHasLiberties(int stone) {
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
    
    private void settleGroups(int move, Map<Integer, List<Integer>> groupsByID, Map<Integer, Integer> stoneGroupIDs) {
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
    
    private void mergeGroups(int group1, int group2, Map<Integer, List<Integer>> groupsByID, Map<Integer, Integer> stoneGroupIDs) {
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
        groupsByID.remove(oldGroupID);
        newGroup.addAll(oldGroup);
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

    public boolean isValidMove(int move, int player) {

        if (player != getCurrentPlayer()) {
            return false;
        }

        if (move == passMove) {
            return true;
        }
        
        if (move == handicapPass && getNumMoves() < 18) {
            return true;
        }

        try {
            checkOutOfBounds(move);
        } catch (IllegalArgumentException e) {
            return false;
        }
        
        if (containsDoublePass()>-1 && move != passMove && getPosition(move) != 0) {
            return true;
        }

        if (getPosition(move) != 0) {
            return false;
        }
        
        if (move == koMove) {
            System.out.println("ko move "+koMove);
            return false;
        }

        boolean suicide = false, repetition = false;
        
        
        if (!isSuicideAllowed() || !isRepetitionAllowed()) {
            addMove(move);

            if (!isSuicideAllowed() && !groupHasLiberties(groupsByPlayerAndID.get(player).get(stoneGroupIDsByPlayer.get(player).get(move)))) {
                undoMove();
                return false;
            }

//       check for Super-Ko
            if (!isRepetitionAllowed()) {
                if (positionHashes.indexOf(positionHashes.get(positionHashes.size()-1)) < positionHashes.size()-1) {
                    undoMove();
                    return false;
                }
            }
            undoMove();
        }
        
        


        return true;
    }

    public int getCurrentPlayer() {
        int cp = 0;
        int dp = containsDoublePass();
        if (dp == -1) {
            cp = getMoves().length % 2 + 1;
        } else if (dp < getMoves().length - 1) {
            cp = 2 - dp % 2;
        } else {
            cp = dp % 2 + 1;
        }
        return cp;
    }



    public void checkOutOfBounds(int move) {
        Coord p = convertMove(move);
        checkOutOfBounds(p.x, p.y);
    }
    public void checkOutOfBounds(int x, int y) {
        if (x < 0 || x >= getGridSizeX() || y < 0 || y >= getGridSizeY()) {
            throw new IllegalArgumentException("Out of bounds");
        }
    }

    private boolean doublePass() {
        int moves[] = getMoves();
        return moves != null &&
                moves.length > 1 &&
                moves[moves.length - 1] == passMove &&
                moves[moves.length - 2] == passMove;
    }
    private int containsDoublePass() {
        boolean hasPass = false;
        for (int i = 0; i < getMoves().length; i++) {
            int move = getMoves()[i];
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
    
    
    public boolean isGameOver() {
        int numMoves = gridState.getNumMoves();
        if (doublePass() && deadStones != null && deadStones.size() > 1 
                && deadStones.get(deadStones.size() - 1) == passMove
                && deadStones.get(deadStones.size() - 2) == passMove) {
            return true;
        }         
        return false;
    }
    
    public int getWinner() {
//        List<Integer> deadStones = getDeadStones();
        for (int dead: deadStones) {
            if (dead < passMove) {
                setPosition(dead, 0);
            }
        }
        getTerritories();
        List<Integer> p1Territory = goTerritoryByPlayer.get(1), p2Territory = goTerritoryByPlayer.get(2);
        int p1Count = p1Territory.size();
//        for (List<Integer> group: groupsByPlayerAndID.get(1).values()) {
//            p1Count += group.size();
//        }
        int p2Count = p2Territory.size() + getMovesForValue(2).size();
//        for (List<Integer> group: groupsByPlayerAndID.get(2).values()) {
//            p2Count += group.size();
//        }
        for (int i = 0; i < getGridSizeX(); i++) {
            for (int j = 0; j < getGridSizeY(); j++) {
                if (getPosition(i,j) == 1) {
                    p1Count += 1;
                } else if (getPosition(i,j) == 1) {
                    p2Count += 1;
                }
            }
        }
        System.out.println(" p1 count ============ " + p1Count);
        System.out.println(" p2 count ============ " + p2Count);
        if (p1Count > p2Count + 7) {
            return 1;
        } else {
            return 2;
        }
    }
    
//    private List<Integer> getDeadStones() {
//        List<Integer> deadStones = new ArrayList<>();
//        boolean hasPass = false, doublePass = false;
//        for (int move: getMoves()) {
//            if (doublePass) {
//                deadStones.add(move);
//                continue;
//            }
//            if (move == passMove) {
//                if (hasPass) {
//                    doublePass = true;
//                } else {
//                    hasPass = true;
//                }
//            } else {
//                hasPass = false;
//            }
//        }
//        return deadStones;
//    }

    

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
    
    private void captureMove(int move, int capturePlayer) {
        setPosition(move, 0);
        capturedAt[capturePlayer][this.captures[capturePlayer]] =
                gridState.getNumMoves() - 1;
        capturedMoves[capturePlayer][this.captures[capturePlayer]] = move;
        this.captures[capturePlayer]++;
        Long last = positionHashes.get(positionHashes.size()-1);
        positionHashes.remove(positionHashes.size()-1);
        positionHashes.add(last ^ rand[3-capturePlayer-1][move]);
    }
    public void undoMove() {
        gridState.clear();
        
        init();
        for (int move: gridState.getMoves()) {
            addMove(move);
        }
    }
    
    private int getEmptyNeighbour(int move) {
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
    private void floodFillWorker(int move, int value) {
        setPosition(move, value);
        int neighbourStone = getEmptyNeighbour(move);
        while (neighbourStone != -1) {
            floodFillWorker(neighbourStone, value);
            neighbourStone = getEmptyNeighbour(move);
        }
    }
    private List<Integer> floodFill(int player) {
        Map<Integer, List<Integer>> groupsByID = getGroupsByPlayerAndID().get(player);
        for (List<Integer> group: groupsByID.values()) {
            for (int move: group) {
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
    private void resetGoBeforeFlood() {
        for (int i = 0; i < getGridSizeX(); i++ ) {
            for (int j = 0; j < getGridSizeY(); j++ ) {
                int pos = getPosition(i,j);
                if (pos != 1 && pos != 2) {
                    setPosition(i,j,0);
                }
            }
        }
    }
    private List<Integer> getMovesForValue(int val) {
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
    private void getTerritories() {
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
    }
    

}
