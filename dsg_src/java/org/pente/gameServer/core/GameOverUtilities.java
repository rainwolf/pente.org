package org.pente.gameServer.core;


public class GameOverUtilities {
	
    public static void updateGameData(
		DSGPlayerStorer dsgPlayerStorer,
        DSGPlayerData winnerPlayerData,
        DSGPlayerGameData winnerPlayerGameData,
        DSGPlayerData loserPlayerData,
        DSGPlayerGameData loserPlayerGameData,
        boolean draw, double k) throws DSGPlayerStoreException {

		boolean winnerInsert = winnerPlayerGameData.getTotalGames() == 0;
		boolean loserInsert = loserPlayerGameData.getTotalGames() == 0;

        DSGPlayerGameData winnerPlayerGameDataPreGameOver = 
            winnerPlayerGameData.getCopy();
    
        if (draw) {
	        winnerPlayerGameData.gameOver(
	            DSGPlayerGameData.DRAW, loserPlayerGameData, k);
	        loserPlayerGameData.gameOver(
	            DSGPlayerGameData.DRAW, winnerPlayerGameDataPreGameOver, k);
        }
        else {
	        winnerPlayerGameData.gameOver(
	            DSGPlayerGameData.WIN, loserPlayerGameData, k);
	        loserPlayerGameData.gameOver(
	            DSGPlayerGameData.LOSS, winnerPlayerGameDataPreGameOver, k);
        }
        
        if (winnerInsert) {
            dsgPlayerStorer.insertGame(winnerPlayerGameData);
        }
        else {
            dsgPlayerStorer.updateGame(winnerPlayerGameData);
        }

        if (loserInsert) {
            dsgPlayerStorer.insertGame(loserPlayerGameData);
        }
        else {
            dsgPlayerStorer.updateGame(loserPlayerGameData);
        }
    }
}
