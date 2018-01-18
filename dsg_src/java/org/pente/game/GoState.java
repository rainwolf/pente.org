package org.pente.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    
    private int koMove, whiteCaptures, blackCaptures;

    private int capturedAt[][];
    private int capturedMoves[][];
    private int captures[];

    private int passMove;
    private int handicapPass;

    private List<Long> positionHashes;
    

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
        this.whiteCaptures = 0;
        this.blackCaptures = 0;
        captures = new int[3];
        capturedAt = new int[3][361];
        capturedMoves = new int[3][361];
        
        passMove = getGridSizeX() * getGridSizeY();
        handicapPass = passMove + 1;
        
        positionHashes = new ArrayList<>();
        positionHashes.add(0L);
    }


    public void addMove(int move) {

        gridState.addMove(move);

        if (0 <= move && move < passMove) {
            int currentPlayer = getCurrentPlayer();
            positionHashes.add(positionHashes.get(positionHashes.size()-1) ^ rand[currentPlayer-1][move]);

            Map<Integer, List<Integer>> groupsByID = getGroupsByPlayerAndID().get(currentPlayer);
            Map<Integer, Integer> stoneGroupIDs = getStoneGroupIDsByPlayer().get(currentPlayer);
            settleGroups(move, groupsByID, stoneGroupIDs);

            int opponent = getCurrentPlayer();
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

        if (getPosition(move) != 0) {
            return false;
        }
        
        if (move == koMove) {
            return false;
        }

        if (!isSuicideAllowed()) {
            if (!stoneHasLiberties(move)) {
                setPosition(move, player);
                if (!groupHasLiberties(getGroupWithoutMerge(move, groupsByPlayerAndID.get(player), stoneGroupIDsByPlayer.get(player)))) {
                    setPosition(move, 0);
                    return false;
                }
                setPosition(move, 0);
            }
        }
        
        

//        TODO check for Super-Ko
        if (!isRepetitionAllowed()) {
            addMove(move);
            if (positionHashes.indexOf(positionHashes.get(positionHashes.size()-1)) < positionHashes.size()-1) {
                undoMove();
                return false;
            }
            undoMove();
        }

        return true;
    }

    public int getCurrentPlayer() {
        return super.getCurrentPlayer();
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

    public boolean isGameOver() {
        int numMoves = gridState.getNumMoves();
        if (numMoves < 2) {
            return false;
        }
        if (gridState.getMove(numMoves - 1) == passMove && gridState.getMove(numMoves - 2) == passMove) {
            return true;
        }
        return false;
    }
    
    public int getWinner() {
        return gridState.getWinner();
    }

    

    @Override
    public long calcHash(long cHash, int p, int move, int rot) {
        cHash ^= rand[p-1][rotateMove(move, rot)];

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

    protected void captureMove(int move, int capturePlayer) {
        gridState.setPosition(move, 0);
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

}
