package org.pente.gameServer.core.test;

import junit.framework.*;

import org.pente.gameServer.core.*;

public class SimpleDSGPlayerGameDataTest extends TestCase {

	private static final double delta = 0.00001d;
	private static final double k = 32;
	
    public static void main(String[] args) {
        junit.textui.TestRunner.main(new String[] {
            SimpleDSGPlayerGameDataTest.class.getName()
        });
    }

    public static Test suite() {
        return new TestSuite(SimpleDSGPlayerGameDataTest.class);
    }
    public SimpleDSGPlayerGameDataTest(String name) {
        super(name);
    }

   
    public void testGameOverOneProvisionalOneNot() {

        DSGPlayerGameData dpgd1 = new SimpleDSGPlayerGameData();
        dpgd1.setLosses(5);
        dpgd1.setWins(1);
        dpgd1.setRating(700);
        DSGPlayerGameData dpgd2 = new SimpleDSGPlayerGameData();
        dpgd2.setLosses(22);
        dpgd2.setWins(5);
        dpgd2.setRating(1701);

        DSGPlayerGameData dpgd3 = dpgd1.getCopy();

        dpgd1.gameOver(DSGPlayerGameData.WIN, dpgd2, k);
        assertEquals(828.6428571428571d, dpgd1.getRating(), delta);

        dpgd2.gameOver(DSGPlayerGameData.LOSS, dpgd3, k);
        assertEquals(1691.4300890099832, dpgd2.getRating(), delta);
    }

    public void testGameOverEstablished() {

        DSGPlayerGameData dpgd1 = new SimpleDSGPlayerGameData();
        dpgd1.setLosses(50);
        dpgd1.setWins(01);
        dpgd1.setRating(700);
        DSGPlayerGameData dpgd2 = new SimpleDSGPlayerGameData();
        dpgd2.setLosses(22);
        dpgd2.setWins(5);
        dpgd2.setRating(1701);

        DSGPlayerGameData dpgd3 = dpgd1.getCopy();

        dpgd1.gameOver(DSGPlayerGameData.LOSS, dpgd2, k);
        //assertEquals(699.8997033000559, dpgd1.getRating(), delta);

        dpgd2.gameOver(DSGPlayerGameData.WIN, dpgd3, k);
        assertEquals(1701.100296699944, dpgd2.getRating(), delta);
    }

    public void testGameOverBothProvisional() {

        DSGPlayerGameData dpgd1 = new SimpleDSGPlayerGameData();
        dpgd1.setLosses(5);
        dpgd1.setWins(1);
        dpgd1.setRating(999);
        DSGPlayerGameData dpgd2 = new SimpleDSGPlayerGameData();
        dpgd2.setLosses(12);
        dpgd2.setWins(5);
        dpgd2.setRating(1277);

        DSGPlayerGameData dpgd3 = dpgd1.getCopy();

        dpgd1.gameOver(DSGPlayerGameData.WIN, dpgd2, k);
        assertEquals(1047.4285714285713d, dpgd1.getRating(), delta);

        dpgd2.gameOver(DSGPlayerGameData.LOSS, dpgd3, k);
        assertEquals(1258.1666666666667d, dpgd2.getRating(), delta);
    }


    public void testGameOverProvisionalBoundary() {

        DSGPlayerGameData dpgd1 = new SimpleDSGPlayerGameData();
        dpgd1.setLosses(0);
        dpgd1.setWins(19);
        dpgd1.setRating(1831);
        DSGPlayerGameData dpgd2 = new SimpleDSGPlayerGameData();
        dpgd2.setLosses(0);
        dpgd2.setWins(1);
        dpgd2.setRating(1500);

        DSGPlayerGameData dpgd3 = dpgd1.getCopy();

        dpgd1.gameOver(DSGPlayerGameData.WIN, dpgd2, k);
        assertEquals(1816.05d, dpgd1.getRating(), delta);

        dpgd2.setWins(21);
        dpgd2.setRating(1555);
        dpgd3.setWins(20);
        dpgd3.gameOver(DSGPlayerGameData.LOSS, dpgd2, k);
        assertEquals(1804.4257628366963, dpgd3.getRating(), delta);
    }


    public void testGameOverNoGames() {

        DSGPlayerGameData dpgd1 = new SimpleDSGPlayerGameData();
        DSGPlayerGameData dpgd2 = new SimpleDSGPlayerGameData();
        DSGPlayerGameData dpgd3 = dpgd1.getCopy();

        assertEquals(true, dpgd1.isEqual(dpgd3));

        dpgd1.gameOver(DSGPlayerGameData.WIN, dpgd2, k);
        assertEquals(1800d, dpgd1.getRating(), delta);
        assertEquals(1, dpgd1.getWins());
        assertEquals(0, dpgd1.getLosses());
        assertEquals(1, dpgd1.getStreak());

        dpgd2.gameOver(DSGPlayerGameData.LOSS, dpgd3, k);
        assertEquals(1400d, dpgd2.getRating(), delta);
        assertEquals(0, dpgd2.getWins());
        assertEquals(1, dpgd2.getLosses());
        assertEquals(-1, dpgd2.getStreak());

        dpgd3 = dpgd1.getCopy();

        dpgd1.gameOver(DSGPlayerGameData.LOSS, dpgd2, k);
        assertEquals(1600d, dpgd1.getRating(), delta);
        assertEquals(1, dpgd1.getWins());
        assertEquals(1, dpgd1.getLosses());
        assertEquals(-1, dpgd1.getStreak());

        dpgd2.gameOver(DSGPlayerGameData.WIN, dpgd3, k);
        assertEquals(1600d, dpgd2.getRating(), delta);
        assertEquals(1, dpgd2.getWins());
        assertEquals(1, dpgd2.getLosses());
        assertEquals(1, dpgd2.getStreak());

        dpgd3 = dpgd1.getCopy();
        dpgd1.gameOver(DSGPlayerGameData.LOSS, dpgd2, k);
        assertEquals(1, dpgd1.getWins());
        assertEquals(2, dpgd1.getLosses());
        assertEquals(-2, dpgd1.getStreak());

        dpgd2.gameOver(DSGPlayerGameData.WIN, dpgd3, k);
        assertEquals(2, dpgd2.getWins());
        assertEquals(1, dpgd2.getLosses());
        assertEquals(2, dpgd2.getStreak());
        
        assertEquals(3, dpgd1.getTotalGames());
    }
    
    public void testProvisionalPlayerLosesRatingsUpProblem()
    {
        DSGPlayerGameData dpgd1 = new SimpleDSGPlayerGameData();
        dpgd1.setWins(1);
        dpgd1.setLosses(1);
        dpgd1.setRating(998d);
        
        
        DSGPlayerGameData dpgd2 = new SimpleDSGPlayerGameData();
        dpgd2.setWins(20);
        dpgd2.setLosses(2);
        dpgd2.setRating(1892d);
        
        DSGPlayerGameData dpgd3 = dpgd1.getCopy();
        
        dpgd1.gameOver(DSGPlayerGameData.LOSS, dpgd2, k);
        assertEquals("Provisional player's rating should be same.",
            998d, dpgd1.getRating(), 0.00d);
    }
}