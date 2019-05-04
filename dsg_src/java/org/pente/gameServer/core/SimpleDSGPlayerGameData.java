/** SimpleDSGPlayerGameData.java
 *  Copyright (C) 2001 Dweebo's Stone Games (http://www.pente.org/)
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, you can find it online at
 *  http://www.gnu.org/copyleft/gpl.txt
 */

package org.pente.gameServer.core;

import java.util.*;
import java.text.*;

public class SimpleDSGPlayerGameData implements 
    DSGPlayerGameData,
    java.io.Serializable {

    private long    playerID;
    private int     game;
    private int     wins;
    private int     losses;
    private int     draws;
    private double  rating;
    private int     streak;
    private Date    lastGameDate;
    private int tourneyWinner;
    private char    computer;
//    private int ratingFloor;
//
//    @Override
//    public int getRatingFloor() {
//        return ratingFloor;
//    }
//
//    @Override
//    public void setRatingFloor(int ratingFloor) {
//        this.ratingFloor = ratingFloor;
//    }

    public SimpleDSGPlayerGameData() {
        rating = 1600;
    }

    public void setPlayerID(long pid) {
        this.playerID = pid;
    }
    public long getPlayerID() {
        return playerID;
    }

    public void setGame(int game) {
        this.game = game;
    }
    public int getGame() {
        return game;
    }

    public void setWins(int wins) {
        this.wins = wins;
    }
    public int getWins() {
        return wins;
    }

    public void setLosses(int losses) {
        this.losses = losses;
    }
    public int getLosses() {
        return losses;
    }

    public void setDraws(int draws) {
        this.draws = draws;
    }
    public int getDraws() {
        return draws;
    }

    public int getTotalGames() {
        return getWins() + getLosses() + getDraws();
    }

	public double getPercentageWins() {
		return ((double) getWins()) / ((double) (wins + losses));
	}

    public void setRating(double rating) {
        this.rating = rating;
    }
    public double getRating() {
        return rating;
    }

    public void setStreak(int streak) {
        this.streak = streak;
    }
    public int getStreak() {
        return streak;
    }

    public void setLastGameDate(Date lastGameDate) {
        this.lastGameDate = lastGameDate;
    }
    public Date getLastGameDate() {
        return lastGameDate;
    }

    public void setComputer(char computer) {
        this.computer = computer;
    }
    public char getComputer() {
        return computer;
    }
    public boolean isComputerScore() {
        return computer == 'Y';
    }
    public boolean isHumanScore() {
        return computer == 'N';
    }    
    public void setTourneyWinner(int winner) {
        this.tourneyWinner = winner;
    }
    public int getTourneyWinner() {
        return tourneyWinner;
    }

    public void gameOver(int result, 
    	DSGPlayerGameData opponentPlayerGameData,
    	double k) {

//    	if (result != DRAW) {
    		updateRating(result, opponentPlayerGameData, k);
//    	}
 
        switch (result) {
            case WIN:
                wins++;
                if (streak <= 0) {
                    streak = 1;
                }
                else {
                    streak++;
                }
                break;

            case LOSS:
                losses++;
                if (streak >= 0) {
                    streak = -1;
                }
                else {
                    streak--;
                }
                break;

            case DRAW:
                draws++;
                streak = 0;
                break;
        }

        lastGameDate = new Date();
    }
    
//    private void updateRatingFloor() {
//        int newFloor = (((int) rating)/100 - 2)*100;
//        if (newFloor > ratingFloor) {
//            setRatingFloor(newFloor);
//        }
//    }

    public void updateRating(int gameResult, 
    	DSGPlayerGameData opponentPlayerGameData, double k) {

        double otherPlayerGames = (double) opponentPlayerGameData.getTotalGames();
//        double otherPlayerGames = (double) (opponentPlayerGameData.getWins() + opponentPlayerGameData.getLosses());

        if (isProvisional()) {
            double gameValue = (opponentPlayerGameData.getRating() + rating) / 2;
            int score = 0;
            if (gameResult == WIN) {
                score = 200;
            } else if (gameResult == LOSS) {
                score = -200;
//            } else { // DRAW
//                score = 100;
            }

            if (!opponentPlayerGameData.isProvisional()) {
                score *= 2;
            }
            
            gameValue += score;

            double newRating = (rating * getTotalGames() + gameValue) / (getTotalGames() + 1);
            if ((gameResult == WIN && newRating > rating) ||
            	(gameResult == LOSS && newRating < rating)
                    || (gameResult == DRAW && newRating > rating && opponentPlayerGameData.getRating() > rating)
                    || (gameResult == DRAW && newRating < rating && opponentPlayerGameData.getRating() < rating)
                    ) {
            	rating = newRating;
            }
        }
        else {
            double w = 1; // WIN
            if (gameResult == LOSS) {
                w = 0;
            } else if (gameResult == DRAW) {
                w = 0.5;
            }

            if (opponentPlayerGameData.isProvisional()) {
                k = k * (otherPlayerGames / 20);
            }

            double powValue = 1 + (java.lang.Math.pow(10, ((opponentPlayerGameData.getRating() - rating) / 400)));
            double diff = (k * (w - (1  / powValue)));
            // prevent the odd cases of losing and increasing in points
            // or winning and losing points
            if ((gameResult == WIN && diff > 0) ||
                    (gameResult == LOSS && diff < 0)
                    || (gameResult == DRAW && diff > 0 && opponentPlayerGameData.getRating() > rating)
                    || (gameResult == DRAW && diff < 0 && opponentPlayerGameData.getRating() < rating)
                    ) {
//                if (rating + diff < ratingFloor) {
//                    rating = (double) ratingFloor;
//                } else {
                    rating += diff;
//                }
            }

//            updateRatingFloor();

        }
    }

    public boolean isProvisional() {
        return getTotalGames() < 20;
//        return wins + losses < 20;
    }

    public DSGPlayerGameData getCopy() {
        DSGPlayerGameData data = new SimpleDSGPlayerGameData();

        data.setDraws(draws);
        data.setGame(game);
        data.setLastGameDate(lastGameDate);
        data.setLosses(losses);
        data.setPlayerID(playerID);
        data.setRating(rating);
        data.setStreak(streak);
        data.setWins(wins);
        data.setComputer(computer);
//        data.setRatingFloor(ratingFloor);

        return data;
    }

    public String getRatingGif() {
  	    String gif = "ratings_";
        if (getTotalGames() > 0) {
           int rating = (int) Math.round(getRating());
 		   if (isProvisional()) {
 		       gif += "white.gif";
 		   }
 		   else {
 			   return getRatingsGifRatingOnly(rating);
 		   }
        }
        else {
        	gif += "white.gif";
        }

        return gif;
    }

    public static String getRatingsGifRatingOnly(int r) {
  	    String gif = "ratings_";
	    if (r >= 1900) {
	        gif += "red.gif";
	    }
	    else if (r >= 1700) {
	        gif += "yellow.gif";
	    }
	    else if (r >= 1400) {
	        gif += "blue.gif";
	    }
	    else if (r >= 1000) {
	        gif += "green.gif";
	    }
	    else {
	        gif += "gray.gif";
	    }
        return gif;
    }
    
    public boolean isEqual(DSGPlayerGameData data) {

        if (data.getDraws() != draws) {
            return false;
        }
        if (data.getGame() != game) {
            return false;
        }
        boolean valid = true;
        if (data.getLastGameDate() == null) {
            valid = lastGameDate == null;
        }
        else {
            valid = datesEqualIgnoreMilliSeconds(data.getLastGameDate(), lastGameDate);
        }
        if (!valid) {
            return false;
        }

        if (data.getLosses() != losses) {
            return false;
        }
        if (data.getPlayerID() != playerID) {
            return false;
        }
        if (data.getRating() != rating) {
            return false;
        }
        if (data.getStreak() != streak) {
            return false;
        }
        if (data.getWins() != wins) {
            return false;
        }
        if (data.isComputerScore() != isComputerScore()) {
            return false;
        }
        
//        if (data.getRatingFloor() != ratingFloor) {
//            return false;
//        }

        return true;
    }

	private static final DateFormat	dateFormat = new SimpleDateFormat("MMddyyyyhhmmss");
    private boolean datesEqualIgnoreMilliSeconds(Date date1, Date date2) {
    	return dateFormat.format(date1).equals(dateFormat.format(date2));
    }
    
    public String toString() {
    	
    	return "Player ID: " + playerID +
    	       ", Game: " + game +
               ", Wins: " + wins +
               ", Losses: " + losses +
               ", Draws: " + draws +
               ", Streak: " + streak +
               ", Rating: " + rating +
                ", Last Game: " + lastGameDate.getTime() 
                + ", Rating Floor: "
//                + ratingFloor
                ;
    }


    public Object clone() {

        DSGPlayerGameData data = null;
        
        try {
            data = (DSGPlayerGameData) super.clone();
        
            if (getLastGameDate() != null) {
                data.setLastGameDate(new Date(getLastGameDate().getTime()));
            }
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        
        return data;
    }
}