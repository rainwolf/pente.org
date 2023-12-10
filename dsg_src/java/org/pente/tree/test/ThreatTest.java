package org.pente.tree.test;

import java.util.*;

import org.pente.tree.*;
import org.pente.game.*;

import junit.framework.TestCase;


public class ThreatTest extends TestCase {

    public ThreatTest(String name) {
        super(name);
    }

    public void testInsertionSort() {
        Threat t = new Threat();
        t.addMove(1);
        t.addMove(2);
        t.addMove(3);

        assertEquals(1, t.moves[0]);
        assertEquals(2, t.moves[1]);
        assertEquals(3, t.moves[2]);
    }

    public void testInsertionSort2() {
        Threat t = new Threat();
        t.addMove(3);
        t.addMove(2);
        t.addMove(1);
        assertEquals(1, t.moves[0]);
        assertEquals(2, t.moves[1]);
        assertEquals(3, t.moves[2]);
    }

    /**
     * make sure that custom insertion sort performs the same as a standard
     * Collections sort
     */
    public void testInsertionSortRandom() {
        for (int j = 0; j < 100; j++) {
            List l = new ArrayList(10);
            Threat t = new Threat();
            for (int i = 0; i < 10; i++) {
                int m = (int) (Math.random() * 361);
                if (!l.contains(Integer.valueOf(m)))
                    l.add(Integer.valueOf(m));
                t.addMove(m);
            }
            Collections.sort(l);
            for (int i = 0; i < l.size(); i++) {
                int m = ((Integer) l.get(i)).intValue();
                assertEquals(m, t.moves[i]);
            }
        }
    }

    public void testSimilarYes() {
        Threat t1 = new Threat();
        t1.addMove(10);
        t1.addMove(8);
        t1.addMove(15);
        t1.addMove(22);
        t1.addMove(3);

        Threat t2 = new Threat();
        t2.addMove(8);
        t2.addMove(3);
        t2.addMove(10);

        assertEquals(1, t1.isSimilar(t2));
        assertEquals(2, t2.isSimilar(t1));
    }

    public void testSimilarYes2() {
        Threat t1 = new Threat();
        t1.addMove(10);
        t1.addMove(8);
        t1.addMove(15);
        t1.addMove(22);
        t1.addMove(3);

        Threat t2 = new Threat();
        t2.addMove(15);
        t2.addMove(22);

        assertEquals(1, t1.isSimilar(t2));
        assertEquals(2, t2.isSimilar(t1));
    }

    public void testSimilarYes3() {
        Threat t1 = new Threat();
        t1.addMove(100);
        t1.addMove(101);
        t1.addMove(50);
        t1.addMove(19);
        t1.addMove(200);

        Threat t2 = new Threat();
        t2.addMove(50);
        t2.addMove(101);

        assertEquals(1, t1.isSimilar(t2));
        assertEquals(2, t2.isSimilar(t1));
    }

    public void testSimilarNo() {
        Threat t1 = new Threat();
        t1.addMove(100);
        t1.addMove(101);
        t1.addMove(50);
        t1.addMove(19);
        t1.addMove(200);

        Threat t2 = new Threat();
        t2.addMove(50);
        t2.addMove(101);
        t2.addMove(102);

        assertEquals(0, t1.isSimilar(t2));
        assertEquals(0, t2.isSimilar(t1));
    }

    public void testSimilarNo2() {
        Threat t1 = new Threat();
        t1.addMove(100);
        t1.addMove(101);

        Threat t2 = new Threat();
        t2.addMove(100);
        t2.addMove(101);

        assertEquals(0, t1.isSimilar(t2));
        assertEquals(0, t2.isSimilar(t1));
    }

    public void testSingleStone() {

        PenteState f = Utils.createState(
                new String[]{"K10"});
        //X, not a threat

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(0, analysis.getNumThreats(2));
    }

    public void testSinglePair() {

        PenteState f = Utils.createState(
                new String[]{"K10", "L10", "K6", "L11"});
        //__XX__

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();

        assertEquals(Threat.TYPE_POTENTIAL_THREE_PAIR, t.type);
        assertEquals(false, t.blocked);
        assertEquals(2, t.player);
        assertEquals(5, t.movesToWin);

        assertMoveList(t, new String[]{"L10", "L11"});
        assertRespList(t, new String[]{"L12", "L9"});
        assertNextList(t, new String[]{"L12", "L9", "L13", "L8"});
    }

    public void testSinglePairOuterBlock1() {

        PenteState f = Utils.createState(
                new String[]{"K10", "L10", "L8", "L11"});
        //0_XX__

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();

        assertEquals(Threat.TYPE_POTENTIAL_THREE_PAIR, t.type);
        assertEquals(false, t.blocked);
        assertEquals(2, t.player);
        assertEquals(5, t.movesToWin);

        assertMoveList(t, new String[]{"L10", "L11"});
        assertRespList(t, new String[]{"L12", "L9"});
        assertNextList(t, new String[]{"L12", "L9", "L13"});
    }


    public void testSinglePairOuterBlock2() {

        PenteState f = Utils.createState(
                new String[]{"K10", "L10", "L8", "L11", "L13"});
        //X_00_X, not a threat at all

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(0, analysis.getNumThreats(2));
    }

    public void testSinglePairOuterBlockWithWall() {

        PenteState f = Utils.createState(
                new String[]{"K10", "B1", "E1", "C1",});
        //W_00_X, not a threat at all

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(0, analysis.getNumThreats(2));
    }

    public void testSinglePairAgainstLeftWall() {

        PenteState f = Utils.createState(
                new String[]{"K10", "B10", "L8", "A10", "L13"});
        //W00__, not a threat at all

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(0, analysis.getNumThreats(2));
    }

    public void testSinglePairAgainstRightWall() {

        PenteState f = Utils.createState(
                new String[]{"K10", "S10", "L8", "T10", "L13"});
        //__00W, not a threat at all

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(0, analysis.getNumThreats(2));
    }

    public void testSinglePairAroundWall() {

        PenteState f = Utils.createState(
                new String[]{"K10", "T10", "L8", "A9", "L13"});
        //0W...W0 on other side

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(0, analysis.getNumThreats(2));
    }

    public void testSinglePairAgainstBottomLeftWall() {

        PenteState f = Utils.createState(
                new String[]{"K10", "A1", "L8", "B1", "L13"});
        //W00__, not a threat at all

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(0, analysis.getNumThreats(2));
    }

    public void testSinglePairNearBottomLeftWall() {

        PenteState f = Utils.createState(
                new String[]{"K10", "B1", "L8", "C1"});
        //W_00__

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertMoveList(t, new String[]{"B1", "C1"});
        assertRespList(t, new String[]{"A1", "D1"});
        assertNextList(t, new String[]{"A1", "D1", "E1"});
    }

    public void testSinglePairNearBottomRightWall() {

        PenteState f = Utils.createState(
                new String[]{"K10", "R1", "L8", "S1"});
        //__00_W

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertMoveList(t, new String[]{"R1", "S1"});
        assertRespList(t, new String[]{"Q1", "T1"});
        assertNextList(t, new String[]{"Q1", "T1", "P1"});
    }

    public void testSinglePairAgainstBottomRightWall() {

        PenteState f = Utils.createState(
                new String[]{"K10", "S1", "L8", "T1"});
        //__00W, not a threat at all

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(0, analysis.getNumThreats(2));
    }

    public void testSinglePairAgainstTopWall() {

        PenteState f = Utils.createState(
                new String[]{"K10", "K19", "L8", "K18"});
        //W00__, not a threat at all

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(0, analysis.getNumThreats(2));
    }

    public void testSplitPair() {

        PenteState f = Utils.createState(
                new String[]{"K10", "L10", "A19", "L12"});
        //__X_X__

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();

        assertEquals(Threat.TYPE_POTENTIAL_THREE_SPLIT, t.type);
        assertEquals(false, t.blocked);
        assertEquals(2, t.player);
        assertEquals(5, t.movesToWin);

        assertMoveList(t, new String[]{"L10", "L12"});
        assertRespList(t, new String[]{"L11", "L9", "L13"});
        assertNextList(t, new String[]{"L11", "L9", "L13", "L8", "L14"});
    }

    public void testSplit3Pair() {

        PenteState f = Utils.createState(
                new String[]{"K10", "L10", "A19", "L14"});
        //__X___X__

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(0, analysis.getNumThreats(2));
    }

    public void testSplitPairOuterBlocked() {

        PenteState f = Utils.createState(
                new String[]{"K10", "L10", "L8", "L12", "L14"});
        //0_X_X_0, not really a possible tria but still counted as one

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();

        assertEquals(Threat.TYPE_POTENTIAL_THREE_SPLIT, t.type);
        assertEquals(false, t.blocked);
        assertEquals(2, t.player);
        assertEquals(5, t.movesToWin);

        assertMoveList(t, new String[]{"L10", "L12"});
        assertRespList(t, new String[]{"L11", "L9", "L13"});
        assertNextList(t, new String[]{"L11", "L9", "L13"});
    }

    public void testSplitPairBlocked() {

        PenteState f = Utils.createState(
                new String[]{"K10", "L10", "L13", "L12"});
        //0X_X__, not considered a threat yet

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(0, analysis.getNumThreats(2));
    }

    public void testSplitPairBlockedWall() {

        PenteState f = Utils.createState(
                new String[]{"K10", "L1", "A5", "L3"});
        //0X_X__, not considered a threat yet

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(0, analysis.getNumThreats(2));
    }

    public void testSplitPairBlockMiddle() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D10", "D11", "D12"});
        //0X_X__, not considered a threat yet

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(0, analysis.getNumThreats(2));
    }

    public void testDoubleSplit() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D10", "R1", "D13"});
        //__X__X__

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();

        assertEquals(Threat.TYPE_POTENTIAL_THREE_SPLIT, t.type);
        assertEquals(false, t.blocked);
        assertEquals(2, t.player);
        assertEquals(5, t.movesToWin);

        assertMoveList(t, new String[]{"D10", "D13"});
        assertRespList(t, new String[]{"D11", "D12", "D9", "D14"});
        assertNextList(t, new String[]{"D11", "D12", "D9", "D14"});
    }

    public void testDoubleSplitBlock() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D10", "D9", "D13"});
        //_0X__X__

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(0, analysis.getNumThreats(2));
    }

    public void testPotentialCapture() {

        PenteState f = Utils.createState(
                new String[]{"K10", "K11", "D9", "K12"});
        //_0XX__

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(1, analysis.getNumThreats(1));
        assertEquals(0, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(1);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_POTENTIAL_CAPTURE, t.type);
        assertEquals(false, t.blocked);
        assertEquals(1, t.player);
        assertEquals(10, t.movesToWin); // indicates not a winning line

        assertMoveList(t, new String[]{"K11", "K12"}); //moves are pieces to be captured in case of potential cap
        assertRespList(t, new String[]{"K13"});
        assertNextList(t, new String[]{"K13"});
    }

    public void testPotentialCapture2() {

        PenteState f = Utils.createState(
                new String[]{"K10", "L11", "J9", "M12", "O14"});
        //00XX_0

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(1, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(1);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_POTENTIAL_CAPTURE, t.type);
        assertEquals(false, t.blocked);
        assertEquals(1, t.player);
        assertEquals(10, t.movesToWin); // indicates not a winning line

        assertMoveList(t, new String[]{"L11", "M12"}); //moves are pieces to be captured in case of potential cap
        assertRespList(t, new String[]{"N13"});
        assertNextList(t, new String[]{"N13"});

        i = analysis.getThreats(2);
        t = (Threat) i.next();
        assertEquals(Threat.TYPE_POTENTIAL_CAPTURE, t.type);
        assertEquals(false, t.blocked);
        assertEquals(2, t.player);
        assertEquals(10, t.movesToWin); // indicates not a winning line

        assertMoveList(t, new String[]{"J9", "K10"}); //moves are pieces to be captured in case of potential cap
        assertRespList(t, new String[]{"H8"});
        assertNextList(t, new String[]{"H8"});
    }

    public void testPlayInSafeCapture() {

        PenteState f = Utils.createState(
                new String[]{"K10", "J11", "G13", "H12"});
        //_0XX0_

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(0, analysis.getNumThreats(2));
    }

    public void testDoubleSplitPotentialFour() {

        PenteState f = Utils.createState(
                new String[]{"K10", "K11", "K16", "K13", "D1", "K15"});
        //_0X_X_X0

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_POTENTIAL_FOUR, t.type);
        assertEquals(false, t.blocked);
        assertEquals(2, t.player);
        assertEquals(3, t.movesToWin);

        assertMoveList(t, new String[]{"K11", "K13", "K15"});
        assertRespList(t, new String[]{"K12", "K14"});
        assertNextList(t, new String[]{"K12", "K14"});
    }

    public void testDoubleSplitPotentialFour2() {

        PenteState f = Utils.createState(
                new String[]{"K10", "K11", "K16", "K14", "D1", "K15"});
        //_0X__XX0

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(1, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(1);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_POTENTIAL_CAPTURE, t.type);
        assertEquals(false, t.blocked);
        assertEquals(1, t.player);
        assertEquals(10, t.movesToWin);

        assertMoveList(t, new String[]{"K14", "K15"});
        assertRespList(t, new String[]{"K13"});
        assertNextList(t, new String[]{"K13"});

        i = analysis.getThreats(2);
        t = (Threat) i.next();
        assertEquals(Threat.TYPE_POTENTIAL_FOUR, t.type);
        assertEquals(false, t.blocked);
        assertEquals(2, t.player);
        assertEquals(3, t.movesToWin);

        assertMoveList(t, new String[]{"K11", "K14", "K15"});
        assertRespList(t, new String[]{"K12", "K13"});
        assertNextList(t, new String[]{"K12", "K13"});
    }

    public void testSimpleTria() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "A1", "D5", "T3", "D6"});
        //__XXX__

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_TRIA, t.type);
        assertEquals(false, t.blocked);
        assertEquals(2, t.player);
        assertEquals(3, t.movesToWin);

        assertMoveList(t, new String[]{"D4", "D5", "D6"});
        assertRespList(t, new String[]{"D2", "D3", "D7", "D8"});
        assertNextList(t, new String[]{"D2", "D3", "D7", "D8"});
    }

    public void testTriaOuterBlock() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "D2", "D5", "T3", "D6"});
        //0_XXX__

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_TRIA, t.type);
        assertEquals(false, t.blocked);
        assertEquals(2, t.player);
        assertEquals(3, t.movesToWin);

        assertMoveList(t, new String[]{"D4", "D5", "D6"});
        assertRespList(t, new String[]{"D3", "D7", "D8"});
        assertNextList(t, new String[]{"D3", "D7", "D8"});
    }


    public void testTriaOuterBlock2() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "D8", "D5", "T3", "D6"});
        //__XXX_0

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_TRIA, t.type);
        assertEquals(false, t.blocked);
        assertEquals(2, t.player);
        assertEquals(3, t.movesToWin);

        assertMoveList(t, new String[]{"D4", "D5", "D6"});
        assertRespList(t, new String[]{"D2", "D3", "D7"});
        assertRespList(t, new String[]{"D2", "D3", "D7"});
    }


    public void testTriaDoubleOuterBlock() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "D8", "D5", "D2", "D6"});
        //0_XXX_0

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_POTENTIAL_FOUR, t.type);
        assertEquals(false, t.blocked);
        assertEquals(2, t.player);
        assertEquals(3, t.movesToWin);

        assertMoveList(t, new String[]{"D4", "D5", "D6"});
        assertRespList(t, new String[]{"D3", "D7"});
        assertRespList(t, new String[]{"D3", "D7"});
    }

    public void testTriaSingleBlock() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "D3", "D5", "T3", "D6"});
        //_0XXX__

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_POTENTIAL_FOUR, t.type);
        assertEquals(true, t.blocked);
        assertEquals(2, t.player);
        assertEquals(3, t.movesToWin);
        assertEquals(3, t.blockedMovesToWin);

        assertMoveList(t, new String[]{"D4", "D5", "D6"});
        assertRespList(t, new String[]{"D7", "D8"});
        assertNextList(t, new String[]{"D7", "D8"});
        assertBlockList(t, new String[]{"D3"});
    }

    public void testTriaSingleBlock2() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "D7", "D5", "T3", "D6"});
        //__XXX0_

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_POTENTIAL_FOUR, t.type);
        assertEquals(true, t.blocked);
        assertEquals(2, t.player);
        assertEquals(3, t.movesToWin);
        assertEquals(3, t.blockedMovesToWin);

        assertMoveList(t, new String[]{"D4", "D5", "D6"});
        assertRespList(t, new String[]{"D3", "D2"});
        assertNextList(t, new String[]{"D3", "D2"});
        assertBlockList(t, new String[]{"D7"});
    }

    public void testTria1_5Block() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "D3", "D5", "D8", "D6"});
        //_0XXX_0

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_BLOCKED, t.type);
        assertEquals(true, t.blocked);
        assertEquals(2, t.player);
        assertEquals(3, t.blockedMovesToWin);

        assertMoveList(t, new String[]{"D4", "D5", "D6"});
        assertEquals(0, t.resps.size());
        assertEquals(0, t.next.size());
        assertBlockList(t, new String[]{"D3"});
    }


    public void testTria1_5Block2() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "D2", "D5", "D7", "D6"});
        //0_XXX0_

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_BLOCKED, t.type);
        assertEquals(true, t.blocked);
        assertEquals(2, t.player);
        assertEquals(3, t.blockedMovesToWin);

        assertMoveList(t, new String[]{"D4", "D5", "D6"});
        assertEquals(0, t.resps.size());
        assertEquals(0, t.next.size());
        assertBlockList(t, new String[]{"D7"});
    }


    public void testTria1_5BlockPlus() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "D3", "D5", "D8", "D6", "A1", "D2"});
        //X0XXX_0
        // fixed now, used to detect both X0XX and X0XXX as separate threats...

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_BLOCKED, t.type);
        assertEquals(true, t.blocked);
        assertEquals(2, t.player);
        assertEquals(1, t.blockedMovesToWin);

        assertMoveList(t, new String[]{"D2", "D4", "D5", "D6"});
        assertEquals(0, t.resps.size());
        assertEquals(0, t.next.size());
        assertBlockList(t, new String[]{"D3"});
    }


    public void testTria1_5BlockPlus2() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "D2", "D5", "D7", "D6", "A1", "D8"});
        //0_XXX0X
        // fixed now, used to detect both X0XX and X0XXX as separate threats...

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_BLOCKED, t.type);
        assertEquals(true, t.blocked);
        assertEquals(2, t.player);
        assertEquals(1, t.blockedMovesToWin);

        assertMoveList(t, new String[]{"D4", "D5", "D6", "D8"});
        assertEquals(0, t.resps.size());
        assertEquals(0, t.next.size());
        assertBlockList(t, new String[]{"D7"});
    }


    public void testTriaDoubleBlock() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "D3", "D5", "D7", "D6"});
        //_0XXX0_

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(0, analysis.getNumThreats(2));
    }

    public void testTriaDoubleBlockThreat() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "D3", "D5", "D7", "D6", "A1", "D8"});
        //_0XXX0X

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_BLOCKED, t.type);
        assertEquals(true, t.blocked);
        assertEquals(2, t.player);
        assertEquals(1, t.blockedMovesToWin);

        assertMoveList(t, new String[]{"D4", "D5", "D6", "D8"});
        assertEquals(0, t.resps.size());
        assertEquals(0, t.next.size());
        assertBlockList(t, new String[]{"D7"});
    }

    public void testTriaDoubleBlockThreat2() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D2", "D3", "D4", "D7", "D5", "A1", "D6"});
        //X0XXX0_

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_BLOCKED, t.type);
        assertEquals(true, t.blocked);
        assertEquals(2, t.player);
        assertEquals(1, t.blockedMovesToWin);

        assertMoveList(t, new String[]{"D2", "D4", "D5", "D6"});
        assertEquals(0, t.resps.size());
        assertEquals(0, t.next.size());
        assertBlockList(t, new String[]{"D3"});
    }

    public void testSimpleSplitTria() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "A1", "D5", "T3", "D7"});
        //__XX_X__

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_TRIA, t.type);
        assertEquals(false, t.blocked);
        assertEquals(2, t.player);
        assertEquals(3, t.movesToWin);

        assertMoveList(t, new String[]{"D4", "D5", "D7"});
        assertRespList(t, new String[]{"D3", "D6", "D8"});
        assertNextList(t, new String[]{"D3", "D6", "D8"});
    }

    public void testSplitTriaOuterBlock() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "D2", "D5", "T3", "D7"});
        //0_XX_X__

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_TRIA, t.type);
        assertEquals(false, t.blocked);
        assertEquals(2, t.player);
        assertEquals(3, t.movesToWin);

        assertMoveList(t, new String[]{"D4", "D5", "D7"});
        assertRespList(t, new String[]{"D3", "D6", "D8"});
        assertNextList(t, new String[]{"D3", "D6", "D8"});
    }

    public void testSplitTriaDoubleOuterBlock() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "D2", "D5", "D9", "D7"});
        //0_XX_X_0

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_TRIA, t.type);
        assertEquals(false, t.blocked);
        assertEquals(2, t.player);
        assertEquals(3, t.movesToWin);

        assertMoveList(t, new String[]{"D4", "D5", "D7"});
        assertRespList(t, new String[]{"D3", "D6", "D8"});
        assertNextList(t, new String[]{"D3", "D6", "D8"});
    }

    public void testSplitTriaBlock() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "D3", "D5", "T3", "D7"});
        //_0XX_X__

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(1, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));


        Iterator i = analysis.getThreats(1);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_POTENTIAL_CAPTURE, t.type);
        assertEquals(false, t.blocked);
        assertEquals(1, t.player);
        assertEquals(10, t.movesToWin);
        assertMoveList(t, new String[]{"D4", "D5"});
        assertRespList(t, new String[]{"D6"});
        assertNextList(t, new String[]{"D6"});

        i = analysis.getThreats(2);
        t = (Threat) i.next();
        assertEquals(Threat.TYPE_POTENTIAL_FOUR, t.type);
        assertEquals(2, t.player);
        assertEquals(3, t.movesToWin);
        assertEquals(true, t.blocked);
        assertEquals(3, t.blockedMovesToWin);

        assertMoveList(t, new String[]{"D4", "D5", "D7"});
        assertRespList(t, new String[]{"D6", "D8"});
        assertNextList(t, new String[]{"D6", "D8"});
        assertBlockList(t, new String[]{"D3"});
    }

    public void testSplitTriaBlock2() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "D3", "D6", "T3", "D7"});
        //_0X_XX__

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));


        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_POTENTIAL_FOUR, t.type);
        assertEquals(2, t.player);
        assertEquals(3, t.movesToWin);
        assertEquals(true, t.blocked);
        assertEquals(3, t.blockedMovesToWin);

        assertMoveList(t, new String[]{"D4", "D6", "D7"});
        assertRespList(t, new String[]{"D5", "D8"});
        assertNextList(t, new String[]{"D5", "D8"});
        assertBlockList(t, new String[]{"D3"});
    }

    public void testSplitTriaBlock3() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "D8", "D6", "T3", "D7"});
        //__X_XX0_

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(1, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));


        Iterator i = analysis.getThreats(1);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_POTENTIAL_CAPTURE, t.type);
        assertEquals(false, t.blocked);
        assertEquals(1, t.player);
        assertEquals(10, t.movesToWin);
        assertMoveList(t, new String[]{"D6", "D7"});
        assertRespList(t, new String[]{"D5"});
        assertNextList(t, new String[]{"D5"});

        i = analysis.getThreats(2);
        t = (Threat) i.next();
        assertEquals(Threat.TYPE_POTENTIAL_FOUR, t.type);
        assertEquals(2, t.player);
        assertEquals(3, t.movesToWin);
        assertEquals(true, t.blocked);
        assertEquals(3, t.blockedMovesToWin);

        assertMoveList(t, new String[]{"D4", "D6", "D7"});
        assertRespList(t, new String[]{"D5", "D3"});
        assertNextList(t, new String[]{"D5", "D3"});
        assertBlockList(t, new String[]{"D8"});
    }

    public void testSplitTriaBlock4() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "D8", "D5", "T3", "D7"});
        //_XX_X0_

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));


        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_POTENTIAL_FOUR, t.type);
        assertEquals(2, t.player);
        assertEquals(3, t.movesToWin);
        assertEquals(true, t.blocked);
        assertEquals(3, t.blockedMovesToWin);

        assertMoveList(t, new String[]{"D4", "D5", "D7"});
        assertRespList(t, new String[]{"D6", "D3"});
        assertNextList(t, new String[]{"D6", "D3"});
        assertBlockList(t, new String[]{"D8"});
    }

    // this one evaluates to both a potential threat for p1 and a blocked
    // split 3 for p2
    public void testSplitTriaInnerBlock1() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "D5", "D6", "A9", "D7"});
        //__X0XX__

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(1, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(1);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_POTENTIAL_CAPTURE, t.type);
        assertEquals(false, t.blocked);
        assertEquals(1, t.player);
        assertEquals(10, t.movesToWin);

        assertMoveList(t, new String[]{"D6", "D7"});
        assertRespList(t, new String[]{"D8"});
        assertNextList(t, new String[]{"D8"});

        i = analysis.getThreats(2);
        t = (Threat) i.next();
        assertEquals(Threat.TYPE_BLOCKED, t.type);
        assertEquals(true, t.blocked);
        assertEquals(2, t.player);
        assertEquals(10, t.movesToWin);

        assertMoveList(t, new String[]{"D4", "D6", "D7"});
        assertRespList(t, new String[]{"D8"});
        assertEquals(0, t.next.size());
        assertBlockList(t, new String[]{"D5"});
    }

    public void testSplitTriaInnerBlockAndSideBlock() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "D5", "D6", "D3", "D7"});
        //_0X0XX__

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(1, analysis.getNumThreats(1));
        assertEquals(0, analysis.getNumThreats(2));
    }

    public void testSplitTriaInnerBlockAndSideBlock2() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "D5", "D6", "D8", "D7"});
        //__X0XX0_

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(0, analysis.getNumThreats(2));
    }

    public void testSplitFourBlockedMiddle() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "D6", "D5", "A1", "D7", "A2", "D8"});
        //_XX0XX_

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(2, analysis.getNumThreats(1)); // 2 potential captures
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_BLOCKED, t.type);
        assertEquals(true, t.blocked);
        assertEquals(1, t.blockedMovesToWin);
        assertEquals(2, t.player);
        assertEquals(10, t.movesToWin);

        assertMoveList(t, new String[]{"D4", "D5", "D7", "D8"});
        assertRespList(t, new String[]{"D3", "D9"});
        assertEquals(0, t.next.size());
        assertBlockList(t, new String[]{"D6"});
    }

    public void testOpenFour() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "A1", "D5", "K5", "D6", "T1", "D7"});
        //__XXXX__

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_OPEN_FOUR, t.type);
        assertEquals(2, t.player);
        assertEquals(1, t.movesToWin);
        assertEquals(false, t.blocked);

        assertMoveList(t, new String[]{"D4", "D5", "D6", "D7"});
        assertRespList(t, new String[]{"D3", "D8"});
        assertNextList(t, new String[]{"D3", "D8"});
    }

    public void testOpenFourBlock1() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "D3", "D5", "K5", "D6", "T1", "D7"});
        //_0XXXX__

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_CLOSED_FOUR, t.type);
        assertEquals(2, t.player);
        assertEquals(1, t.movesToWin);
        assertEquals(false, t.blocked);

        assertMoveList(t, new String[]{"D4", "D5", "D6", "D7"});
        assertRespList(t, new String[]{"D8"});
        assertNextList(t, new String[]{"D8"});
    }

    public void testOpenFourBlock2() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "D8", "D5", "K5", "D6", "T1", "D7"});
        //__XXXX0_

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_CLOSED_FOUR, t.type);
        assertEquals(2, t.player);
        assertEquals(1, t.movesToWin);
        assertEquals(false, t.blocked);

        assertMoveList(t, new String[]{"D4", "D5", "D6", "D7"});
        assertRespList(t, new String[]{"D3"});
        assertNextList(t, new String[]{"D3"});
    }

    public void testOpenFourSplit1() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "D3", "D5", "D9", "D6", "T1", "D8"});
        //_0XXX_X0_

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_CLOSED_FOUR, t.type);
        assertEquals(2, t.player);
        assertEquals(1, t.movesToWin);
        assertEquals(false, t.blocked);

        assertMoveList(t, new String[]{"D4", "D5", "D6", "D8"});
        assertRespList(t, new String[]{"D7"});
        assertNextList(t, new String[]{"D7"});
    }

    public void testOpenFourSplit2() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "A1", "D5", "K5", "D7", "T1", "D8"});
        //__XX_XX__

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1)); //2 captures
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_CLOSED_FOUR, t.type);
        assertEquals(2, t.player);
        assertEquals(1, t.movesToWin);
        assertEquals(false, t.blocked);

        assertMoveList(t, new String[]{"D4", "D5", "D7", "D8"});
        assertRespList(t, new String[]{"D6"});
        assertNextList(t, new String[]{"D6"});
    }

    public void testOpenFourSplit2DoubleCap() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "D3", "D5", "D9", "D7", "T1", "D8"});
        //_0XX_XX0_

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(2, analysis.getNumThreats(1)); //2 captures
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(1);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_POTENTIAL_CAPTURE, t.type);
        assertEquals(1, t.player);
        assertRespList(t, new String[]{"D6"});
        assertNextList(t, new String[]{"D6"});
        t = (Threat) i.next();
        assertEquals(Threat.TYPE_POTENTIAL_CAPTURE, t.type);
        assertEquals(1, t.player);
        assertRespList(t, new String[]{"D6"});
        assertNextList(t, new String[]{"D6"});

        i = analysis.getThreats(2);
        t = (Threat) i.next();
        assertEquals(Threat.TYPE_CLOSED_FOUR, t.type);
        assertEquals(2, t.player);
        assertEquals(1, t.movesToWin);
        assertEquals(false, t.blocked);

        assertMoveList(t, new String[]{"D4", "D5", "D7", "D8"});
        assertRespList(t, new String[]{"D6"});
        assertNextList(t, new String[]{"D6"});
    }

    public void testFourDoubleBlock() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "D3", "D5", "D8", "D6", "T1", "D7"});
        //_0XXXX0_

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_BLOCKED, t.type);
        assertEquals(2, t.player);
        assertEquals(10, t.movesToWin);
        assertEquals(true, t.blocked);
        assertEquals(1, t.blockedMovesToWin);

        assertMoveList(t, new String[]{"D4", "D5", "D6", "D7"});
        assertEquals(0, t.next.size());
        assertEquals(0, t.resps.size());
        assertBlockList(t, new String[]{"D3", "D8"});
    }


    public void testDoubleSplitFours() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "A1", "D6", "B3", "D7", "T1", "D8",
                        "R5", "D10"});
        //_X_XXX_X

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1)); // 2 potential captures
        assertEquals(2, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_CLOSED_FOUR, t.type);
        assertEquals(false, t.blocked);
        assertEquals(2, t.player);
        assertEquals(1, t.movesToWin);

        assertMoveList(t, new String[]{"D6", "D7", "D8", "D10"});
        assertRespList(t, new String[]{"D9"});
        assertNextList(t, new String[]{"D9"});

        t = (Threat) i.next();
        assertEquals(Threat.TYPE_CLOSED_FOUR, t.type);
        assertEquals(false, t.blocked);
        assertEquals(2, t.player);
        assertEquals(1, t.movesToWin);

        assertMoveList(t, new String[]{"D4", "D6", "D7", "D8"});
        assertRespList(t, new String[]{"D5"});
        assertNextList(t, new String[]{"D5"});
    }

    public void testDoubleEqualSplitFours() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "A1", "D5", "B3", "D7", "T1", "D8",
                        "R5", "D10", "K1", "D11"});
        //XX_XX_XX

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(2, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_CLOSED_FOUR, t.type);
        assertEquals(false, t.blocked);
        assertEquals(2, t.player);
        assertEquals(1, t.movesToWin);

        assertMoveList(t, new String[]{"D7", "D8", "D10", "D11"});
        assertRespList(t, new String[]{"D9"});
        assertNextList(t, new String[]{"D9"});


        t = (Threat) i.next();
        assertEquals(Threat.TYPE_CLOSED_FOUR, t.type);
        assertEquals(false, t.blocked);
        assertEquals(2, t.player);
        assertEquals(1, t.movesToWin);

        assertMoveList(t, new String[]{"D4", "D5", "D7", "D8"});
        assertRespList(t, new String[]{"D6"});
        assertNextList(t, new String[]{"D6"});
    }

    public void testDoubleSplitFours2() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "D3", "D5", "B17", "D6", "T1", "D8",
                        "R5", "D10", "K1", "D11", "D13", "D12"});
        //0XXX_X_XXX0

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(2, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_CLOSED_FOUR, t.type);
        assertEquals(false, t.blocked);
        assertEquals(2, t.player);
        assertEquals(1, t.movesToWin);

        assertMoveList(t, new String[]{"D8", "D10", "D11", "D12"});
        assertRespList(t, new String[]{"D9"});
        assertNextList(t, new String[]{"D9"});

        t = (Threat) i.next();
        assertEquals(Threat.TYPE_CLOSED_FOUR, t.type);
        assertEquals(false, t.blocked);
        assertEquals(2, t.player);
        assertEquals(1, t.movesToWin);

        assertMoveList(t, new String[]{"D4", "D5", "D6", "D8"});
        assertRespList(t, new String[]{"D7"});
        assertNextList(t, new String[]{"D7"});
    }

    public void testDoubleSplitThrees() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "B17", "D6", "K1", "D7", "T1", "D9"});
        //_X_XX_X_, evaluates to 2 split tria's
        // that makes middle splits have a high rank since they will look
        // like double-fours, but thats not really the case

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(2, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_TRIA, t.type);
        assertEquals(false, t.blocked);
        assertEquals(2, t.player);
        assertEquals(3, t.movesToWin);

        assertMoveList(t, new String[]{"D6", "D7", "D9"});
        assertRespList(t, new String[]{"D5", "D8", "D10"});
        assertNextList(t, new String[]{"D5", "D8", "D10"});

        t = (Threat) i.next();
        assertEquals(Threat.TYPE_TRIA, t.type);
        assertEquals(false, t.blocked);
        assertEquals(2, t.player);
        assertEquals(3, t.movesToWin);

        assertMoveList(t, new String[]{"D4", "D6", "D7"});
        assertRespList(t, new String[]{"D5", "D8", "D3"});
        assertNextList(t, new String[]{"D5", "D8", "D3"});
    }

    public void testDoubleSplitThrees2() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "B17", "D5", "K1", "D7", "T1", "D9"});
        //_XX_X_X_, end X is cutoff

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_TRIA, t.type);
        assertEquals(false, t.blocked);
        assertEquals(2, t.player);
        assertEquals(3, t.movesToWin);

        assertMoveList(t, new String[]{"D4", "D5", "D7"});
        assertRespList(t, new String[]{"D6", "D8", "D3"});
        assertNextList(t, new String[]{"D6", "D8", "D3"});
    }


    public void testDoubleSplitThrees3() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "B17", "D5", "K1", "D7", "T1", "D9", "A1", "D10"});
        //_XX_X_XX_

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(2, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_TRIA, t.type);
        assertEquals(false, t.blocked);
        assertEquals(2, t.player);
        assertEquals(3, t.movesToWin);

        assertMoveList(t, new String[]{"D7", "D9", "D10"});
        assertRespList(t, new String[]{"D6", "D8", "D11"});
        assertNextList(t, new String[]{"D6", "D8", "D11"});

        t = (Threat) i.next();
        assertEquals(Threat.TYPE_TRIA, t.type);
        assertEquals(false, t.blocked);
        assertEquals(2, t.player);
        assertEquals(3, t.movesToWin);

        assertMoveList(t, new String[]{"D4", "D5", "D7"});
        assertRespList(t, new String[]{"D6", "D8", "D3"});
        assertNextList(t, new String[]{"D6", "D8", "D3"});
    }


    public void testSplit4Plus() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "B17", "D6", "K1", "D7", "T1", "D9", "A1", "D10"});
        //_X_XX_XX_

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(2, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_CLOSED_FOUR, t.type);
        assertEquals(false, t.blocked);
        assertEquals(2, t.player);
        assertEquals(1, t.movesToWin);

        assertMoveList(t, new String[]{"D6", "D7", "D9", "D10"});
        assertRespList(t, new String[]{"D8"});
        assertNextList(t, new String[]{"D8"});

        t = (Threat) i.next();
        assertEquals(Threat.TYPE_TRIA, t.type);
        assertEquals(false, t.blocked);
        assertEquals(2, t.player);
        assertEquals(3, t.movesToWin);

        assertMoveList(t, new String[]{"D4", "D6", "D7"});
        assertRespList(t, new String[]{"D5", "D3", "D8"});
        assertNextList(t, new String[]{"D5", "D3", "D8"});
    }

    public void test2DoubleSplit3s() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "B17", "D6", "K1", "D8", "T1", "D10"});
        //_X_X_X_X_

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(2, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_POTENTIAL_FOUR, t.type);
        assertEquals(false, t.blocked);
        assertEquals(2, t.player);
        assertEquals(3, t.movesToWin);

        assertMoveList(t, new String[]{"D6", "D8", "D10"});
        assertRespList(t, new String[]{"D7", "D9"});
        assertNextList(t, new String[]{"D7", "D9"});

        t = (Threat) i.next();
        assertEquals(Threat.TYPE_POTENTIAL_FOUR, t.type);
        assertEquals(false, t.blocked);
        assertEquals(2, t.player);
        assertEquals(3, t.movesToWin);

        assertMoveList(t, new String[]{"D4", "D6", "D8"});
        assertRespList(t, new String[]{"D5", "D7"});
        assertNextList(t, new String[]{"D5", "D7"});
    }

    public void testPotetialFourPlus1() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "B17", "D5", "K1", "D8", "T1", "D9"});
        //_XX__XX__

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_POTENTIAL_FOUR, t.type);
        assertEquals(false, t.blocked);
        assertEquals(2, t.player);
        assertEquals(3, t.movesToWin);

        assertMoveList(t, new String[]{"D4", "D5", "D8", "D9"});
        assertRespList(t, new String[]{"D3", "D6", "D7", "D10"});
        assertNextList(t, new String[]{"D3", "D6", "D7", "D10"});
    }

    public void testTriaAndPotentialFour() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "B17", "D5", "K1", "D6", "T1", "D9", "K16", "D10"});
        //_XXX__XX__

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_TRIA, t.type);
        assertEquals(false, t.blocked);
        assertEquals(2, t.player);
        assertEquals(3, t.movesToWin);

        assertMoveList(t, new String[]{"D4", "D5", "D6"});
        assertRespList(t, new String[]{"D2", "D3", "D7", "D8"});
        assertNextList(t, new String[]{"D3", "D7", "D8", "D2"});
    }

    public void testClosedFourPlus1() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "B17", "D5", "K1", "D6", "T1", "D8", "K16", "D9"});
        //_XXX_XX__

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(0, analysis.getNumThreats(1));
        assertEquals(1, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(2);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_CLOSED_FOUR, t.type);
        assertEquals(false, t.blocked);
        assertEquals(2, t.player);
        assertEquals(1, t.movesToWin);

        assertMoveList(t, new String[]{"D4", "D5", "D6", "D8", "D9"});
        assertRespList(t, new String[]{"D7"});
        assertNextList(t, new String[]{"D7"});
    }

    public void testBlockedFourPlusPotentialFour() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "B17", "D6", "K1", "D7", "D8", "D9", "K16", "D10"});
        //X_XX0XX

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(2, analysis.getNumThreats(1));
        assertEquals(2, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(1);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_POTENTIAL_CAPTURE, t.type);
        t = (Threat) i.next();
        assertEquals(Threat.TYPE_POTENTIAL_CAPTURE, t.type);

        i = analysis.getThreats(2);
        t = (Threat) i.next();
        assertEquals(Threat.TYPE_BLOCKED, t.type);
        assertEquals(true, t.blocked);
        assertEquals(1, t.blockedMovesToWin);
        assertEquals(2, t.player);
        assertEquals(10, t.movesToWin);

        assertMoveList(t, new String[]{"D6", "D7", "D9", "D10"});
        assertRespList(t, new String[]{"D5", "D11"});
        assertNextList(t, new String[]{});
        assertBlockList(t, new String[]{"D8"});

        t = (Threat) i.next();
        assertEquals(Threat.TYPE_POTENTIAL_FOUR, t.type);
        assertEquals(true, t.blocked);
        assertEquals(3, t.blockedMovesToWin);
        assertEquals(2, t.player);
        assertEquals(3, t.movesToWin);

        assertMoveList(t, new String[]{"D4", "D6", "D7"});
        assertRespList(t, new String[]{"D5", "D3"});
        assertNextList(t, new String[]{"D5", "D3"});
        assertBlockList(t, new String[]{"D8"});
    }

    public void testBlockedFourCapture() {

        PenteState f = Utils.createState(
                new String[]{"K10", "D4", "D3", "D5", "D8", "D7"});
        //0XX_X0

        PenteAnalyzer analyzer = new PenteAnalyzer(f);
        PositionAnalysis analysis = analyzer.analyzeMove();
        assertEquals(1, analysis.getNumThreats(1));
        assertEquals(0, analysis.getNumThreats(2));

        Iterator i = analysis.getThreats(1);
        Threat t = (Threat) i.next();
        assertEquals(Threat.TYPE_POTENTIAL_CAPTURE, t.type);
        assertEquals(false, t.blocked);
        assertEquals(1, t.player);

        assertMoveList(t, new String[]{"D4", "D5"});
        assertRespList(t, new String[]{"D6"});
        assertNextList(t, new String[]{"D6"});
    }

    //TODO tests
    //XXX0XX_XX - XXX0X and XX_XX (might resolve as XXX0X and XX0XX and XX_XX)

    //XXX_XXX
    //XXXX_X
    //XXXX__X
    //XXX__X
    //XXX__XXX ?


    // utility methods to assert threat properties
    private void assertMoveList(Threat t, String moves[]) {
        assertEquals(moves.length, t.numMoves);
        outer:
        for (int i = 0; i < moves.length; i++) {
            for (int j = 0; j < t.numMoves; j++) {
                if (Utils.getMove(moves[i]) == t.getMove(j)) {
                    continue outer;
                }
            }
            fail("Move " + moves[i] + " not found in threat.");
        }
    }

    private void assertRespList(Threat t, String moves[]) {
        assertEquals(moves.length, t.resps.size());
        outer:
        for (int i = 0; i < moves.length; i++) {
            for (int j = 0; j < t.resps.size(); j++) {
                if (Utils.getMove(moves[i]) == ((Integer) t.resps.get(j)).intValue()) {
                    continue outer;
                }
            }
            fail("Response " + moves[i] + " not found in threat.");
        }
    }

    private void assertNextList(Threat t, String moves[]) {
        assertEquals(moves.length, t.next.size());
        outer:
        for (int i = 0; i < moves.length; i++) {
            for (int j = 0; j < t.next.size(); j++) {
                if (Utils.getMove(moves[i]) == ((Integer) t.next.get(j)).intValue()) {
                    continue outer;
                }
            }
            fail("Next move " + moves[i] + " not found in threat.");
        }
    }

    private void assertBlockList(Threat t, String moves[]) {
        assertEquals(moves.length, t.numBlocks);
        outer:
        for (int i = 0; i < moves.length; i++) {
            for (int j = 0; j < t.numBlocks; j++) {
                if (Utils.getMove(moves[i]) == t.blockPositions[j]) {
                    continue outer;
                }
            }
            fail("Block move " + moves[i] + " not found in threat.");
        }
    }
}