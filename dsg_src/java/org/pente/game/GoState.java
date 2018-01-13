package org.pente.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private void init() {
        this.groupsByPlayerAndID = new HashMap<>();
        this.groupsByPlayerAndID.put(1, new HashMap<>());
        this.groupsByPlayerAndID.put(2, new HashMap<>());
        this.stoneGroupIDsByPlayer = new HashMap<>();
        this.stoneGroupIDsByPlayer.put(1, new HashMap<>());
        this.stoneGroupIDsByPlayer.put(2, new HashMap<>());
        this.koMove = -1;
    }


    public void addMove(int move) {
        int currentPlayer = getCurrentPlayer();

        gridState.addMove(move);
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
        for (int stone: group) {
            setPosition(stone, 0);
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

        if (move == getGridSizeX()*getGridSizeX()) {
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
                if (!groupHasLiberties(getGroupWithoutMerge(move, groupsByPlayerAndID.get(player), stoneGroupIDsByPlayer.get(player)))) {
                    return false;
                }
            }
        }
        
        

//        TODO check for Super-Ko
        if (!isRepetitionAllowed()) {
            
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
        int passMove = getGridSizeX()*getGridSizeX();
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
        return 0;
    }

    @Override
    public void printBoard() {

    }


    public Map<Integer, Map<Integer, List<Integer>>> getGroupsByPlayerAndID() { return groupsByPlayerAndID; }
    public void setGroupsByPlayerAndID(Map<Integer, Map<Integer, List<Integer>>> groupsByPlayerAndID) { this.groupsByPlayerAndID = groupsByPlayerAndID; }
    public Map<Integer, Map<Integer, Integer>> getStoneGroupIDsByPlayer() { return stoneGroupIDsByPlayer; }
    public void setStoneGroupIDsByPlayer(Map<Integer, Map<Integer, Integer>> stoneGroupIDsByPlayer) { this.stoneGroupIDsByPlayer = stoneGroupIDsByPlayer; }

}
