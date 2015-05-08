//package org.pente.game.test;
//
//import java.awt.*;
//
//import junit.framework.*;
//
//import org.pente.game.*;
//import org.pente.gameServer.core.*;
//
//public class DataHasherTester extends TestCase {
//
//    public DataHasherTester(String name) {
//        super(name);
//    }
//
//    public void testGame1() {
//
//        PenteState state = (PenteState) 
//            GridStateFactory.createGridState(GridStateFactory.PENTE);
//        addMoves(new String[] { "K10", "J11", "K13", "J13", "J12", "K11", "G11",
//            "K12", "L11", "K9", "H14", "K12", "F14", "K11", "K13", "K10", "K8",
//            "H10", "G14", "J14", "G13", "G10", "G12", "G15", "J10", "E14", "H12",
//            "E15", "F10", "G10", "E9", "J13", "D8" }, state);
//        assertEquals(true, state.isGameOver());
//
//        int expectedKeys[] = new int[] { 82592, 3306308, 4186757, 4023431,
//            3701646, 3778698, 4163588, 3975694, 4053463, 2969442, 1123965,
//            1512495, 1959751, 1518044, 7201276, 7300284, 6680535, 6878957,
//            6993769, 7134061, 6973637, 7042081, 6884188, 6927652, 7180708,
//            6465535, 6684274, 7126583, 12464918, 12427818, 12097714, 11863583,
//            12436931 };
//        int expectedRotations[] = new int[] { 0, 1, 2, 2, 3, 2, 2, 5, 1, 6, 7,
//            7, 7, 0, 2, 2, 5, 1, 1, 4, 4, 1, 6, 3, 5, 2, 2, 3, 2, 2, 7, 5, 5 };
//        
//        state.updateHashes();
//        for (int i = 0; i < expectedKeys.length; i++) {
//            assertEquals(expectedKeys[i], state.getHash(i));
//            assertEquals(expectedRotations[i], state.getRotation(i));
//        }
//    }
//
//    public void testGame3() {
//        PenteState state = (PenteState) 
//            GridStateFactory.createGridState(GridStateFactory.PENTE);
//        addMoves(new String[] { "K10", "L9", "N10", "N9", "M9", "L8", "L10",
//            "O11" }, state);
//        assertEquals(0, state.getNumCaptures(1));
//        assertEquals(2, state.getNumCaptures(2));
//
//        int expectedKeys[] = new int[] { 82592, 3306308, 4186757, 4023431,
//            3701646, 4012841, 3534029, 44098895 };
//        int expectedRotations[] = new int[] { 0, 0, 7, 7, 6, 3, 3, 2 };
//  
//        //Vector h = new Vector();
//        //Vector r = new Vector();
//        //MarksGameDataHasher.getInstance(0).createHashKeys(state, h, r);
//        
//        state.updateHashes();
//        for (int i = 0; i < expectedKeys.length; i++) {
//            assertEquals(expectedKeys[i], state.getHash(i));
//            assertEquals(expectedRotations[i], state.getRotation(i));
//        }
//    }
//
//    public void testGame2() {
//        
//        PenteState state = (PenteState) 
//            GridStateFactory.createGridState(GridStateFactory.PENTE);
//        addMoves(new String[] { "K10", "J11", "K13", "J13", "J12", "K11", "G11",
//            "K12", "L11", "K9", "H14", "K12", "F14", "K11", "K13", "K10", "K8",
//            "H10", "G14", "J14", "G13", "G10", "G12", "G15", "J10", "E14", "H12",
//            "E15", "F10", "G10", "E9", "J13", "D8" }, state);
//        assertEquals(true, state.isGameOver());
//
//        int expectedHash = 12436931;
//        state.updateHashes();
//        int actualHash = state.getHash();
//        assertEquals(expectedHash, actualHash);
//    }
//
//    public void testGamesDifferentPathsEqualWithCapture() {
//        
//        PenteState state1 = (PenteState)
//            GridStateFactory.createGridState(GridStateFactory.PENTE);
//        addMoves(new String[] { "K10", "L10", "K9", "M10", "N10" }, state1);
//        assertEquals(2, state1.getNumCaptures(1));
//        state1.updateHashes();
//        int hash1 = state1.getHash();
//
//        PenteState state2 = (PenteState)
//            GridStateFactory.createGridState(GridStateFactory.PENTE);
//        addMoves(new String[] { "K10", "J10", "K11", "H10", "G10" }, state2);
//        assertEquals(2, state2.getNumCaptures(1));
//        state2.updateHashes();
//        int hash2 = state2.getHash();
//        
//        assertEquals(hash1, hash2);
//    }
//
//    public void testGamesDifferentPathsEqual() {
//        
//        PenteState state1 = (PenteState)
//            GridStateFactory.createGridState(GridStateFactory.PENTE);
//        addMoves(new String[] { "K10", "L10", "N10", "M10", "K9" }, state1);
//        state1.updateHashes();
//        int hash1 = state1.getHash();
//
//        PenteState state2 = (PenteState)
//            GridStateFactory.createGridState(GridStateFactory.PENTE);
//        addMoves(new String[] { "K10", "J10", "G10", "H10", "K11" }, state2);
//        state2.updateHashes();
//        int hash2 = state2.getHash();
//        
//        assertEquals(hash1, hash2);
//    }
//
//    public void testUndo() {
//        
//        PenteState state1 = (PenteState)
//            GridStateFactory.createGridState(GridStateFactory.PENTE);
//        addMoves(new String[] { "K10", "L10", "N10", "M10", "K9" }, state1);
//        state1.undoMove();
//        addMoves(new String[] { "K9", "A1" }, state1);
//        state1.updateHashes();
//        int hash1 = state1.getHash();
//
//        PenteState state2 = (PenteState)
//            GridStateFactory.createGridState(GridStateFactory.PENTE);
//        addMoves(new String[] { "K10", "L10", "N10", "M10", "K9", "A1" }, state2);
//        state2.updateHashes();
//        int hash2 = state2.getHash();
//        
//        assertEquals(hash1, hash2);
//    }
//    
//    public void testPoofEqualsCapture() {
//        
//        PenteState state1 = (PenteState)
//            GridStateFactory.createGridState(GridStateFactory.PENTE);
//        addMoves(new String[] { "K10", "L10", "K9", "M10", "N10" }, state1);
//        assertEquals(2, state1.getNumCaptures(1));
//        state1.updateHashes();
//        int hash1 = state1.getHash();
//        
//        SimplePoofPenteState state2 = (SimplePoofPenteState)
//            GridStateFactory.createGridState(GridStateFactory.POOF_PENTE);
//        addMoves(new String[] { "K10", "L10", "N10", "M10", "K9" }, state2);
//        state2.updateHashes();
//        int hash2 = state2.getHash();
//        
//        assertEquals(hash1, hash2);
//    }
//
//    private void addMoves(String moves[], GridState state) {
//        
//        for (int i = 0; i < moves.length; i++) {
//            state.addMove(getMove(moves[i]));
//        }
//    }
//    
//    GridState gridState = GridStateFactory.createGridState(GridStateFactory.GOMOKU);
//    GridCoordinates coordinates = new AlphaNumericGridCoordinates(19, 19);
//    private int getMove(String move) {
//        
//        Point p = coordinates.getPoint(move);
//        return gridState.convertMove(p.x, p.y);
//    }
//}
