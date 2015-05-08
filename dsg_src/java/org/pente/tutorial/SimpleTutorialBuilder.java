package org.pente.tutorial;

/** SimpleTutorialBuilder.java
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

import org.pente.game.*;

public class SimpleTutorialBuilder implements TutorialBuilder {

    /**
     * Constructor for SimpleTutorialBuilder.
     */
    public SimpleTutorialBuilder() {
    }

    /**
     * @see org.pente.tutorial.TutorialBuilder#buildTutorial()
     */
    public TutorialController buildTutorial() {
        
        TutorialController controller = new SimpleTutorialController();
        
        controller.addSection("Rules");
 
        controller.addStep("Rules", new TextTutorialStep(
            "Welcome to the Pente.org Tutorials!\n" +
            "The current selected tutorial is \"Rules\".  To change to a " +
            "different tutorial, use the selection box above.  " +
            "Once you have selected a tutorial, navigate through the " +
            "tutorial with the << and >> buttons.", 
            "Pente.org Tutorials"));

        controller.addStep("Rules", new TextTutorialStep(
            "Because Pente is the most popular game by far at Pente.org I'll start " +            "by explaining the rules to Pente, then discuss the other games and " +            "how they compare to pente.\n\nPente is a great game because you can learn how to play in 5 " +
            "minutes and complete casual games in about the same time. " +
            "However, it is also an excellent game for serious competitive gamers " +
            "because there is plenty of strategy that can take years to learn " +
            "and master.  So enjoy the tutorial, I promise it won't take long.\n\n" +
            "Author: dweebo",
            "Games"));

        MultiTutorialStep multiStep = new MultiTutorialStep();
        multiStep.addTutorialStep(new TextTutorialStep(
            "Above you see the standard Pente/Keryo-Pente/Gomoku board.  " +
            "The board is simple, it's just a 19x19 grid!  " + 
            "On the board, stones are placed on the grid.\n\n" +
            "Interactive - Click on the board and place a stone " +
            "at the middle of the board.", "Game Board"));
        multiStep.addTutorialStep(new InteractiveMoveTutorialStep("K10"));
        controller.addStep("Rules", multiStep);

        multiStep = new MultiTutorialStep();
        multiStep.addTutorialStep(new TextTutorialStep(
            "The first move is always made by white at the center " +
            "of the board at K10.  Play alternates between " +
            "black and white until the game is won.  The coordinate " +
            "system uses letters A-T and numbers 1-19.  Please note " +
            "that the letter I is not used to avoid confusion between " +
            "it and the letter L.\n\n" +
            "Interactive - click on the board at N11.",
            "Pente Coordinate System"));
        multiStep.addTutorialStep(new MoveTutorialStep(180));
        multiStep.addTutorialStep(new InteractiveMoveTutorialStep("N11"));
        controller.addStep("Rules", multiStep);
        
        multiStep = new MultiTutorialStep();
        multiStep.addTutorialStep(new TextTutorialStep(
            "There is an additional rule used to make the game more fair " +
            "for both players that is commonly used.  The rule restricts " +
            "where white (player 1) can play on the 2nd move.  The 2nd move " +
            "must be played more than 2 grids away from the center, (on or outside " +
            "of box marked off by the smaller circles at G7, N7, G13 and N13).\n\n" +
            "Interactive - click on the board to make a 2nd move for white " +
            "without violating the tournament rule.",
            "Pente Tournament Rule"));
        multiStep.addTutorialStep(new MoveTutorialStep(new int[] { 180, 164 }));
        multiStep.addTutorialStep(new InteractiveMoveTutorialStep(
            new InteractiveMoveLogic() {
                private final GridState state =
                    GridStateFactory.createGridState(GridStateFactory.PENTE);

                public boolean isMoveValid(int x, int y) {
                    state.clear();
                    state.addMove(180);
                    state.addMove(164);
                    return state.isValidMove(state.convertMove(x, y), 1);
                }
            }));

        controller.addStep("Rules", multiStep);

        multiStep = new MultiTutorialStep();
        multiStep.addTutorialStep(new TextTutorialStep(
            "The game is won when 1 of 2 things happens. " +
            "The first way to win is to get 5 of your stones in a row. " +
            "The stones can be horizontal, vertical or diagonal.\n\n" +
            "Interactive - click on the board to make white win.",
            "How to Win!"));
        multiStep.addTutorialStep(new MoveTutorialStep(
            new String[] { "K10", "L9", "N10", "M9", "L10", "M10", "N11", "M11",
                           "K8", "M10", "M8", "M13", "M12", "L13", "N8", "O10",
                           "M12", "O8", "L8", "J8", "L11", "J10", "N13", "K10",
                           "L10", "K12", "O14", "P15", "L7", "L9", "J11", "L9" }));
        multiStep.addTutorialStep(new InteractiveMoveTutorialStep("K10"));
        controller.addStep("Rules", multiStep);
        
        multiStep = new MultiTutorialStep();
        multiStep.addTutorialStep(new TextTutorialStep(
            "You can also win if you get MORE than 5 of your stones in a row.\n\n" +
            "Interactive - click on the board to make black win with more than " +
            "5 stones in a row.",
            "How to Win!"));
        multiStep.addTutorialStep(new MoveTutorialStep(
            new String[] { "K10", "L9", "K7", "K11", "H10", "J10", "H9", "L12",
                           "H8", "H11", "H7", "H6", "J9", "L11", "L10", "M10",
                           "L10", "K12", "L13", "L11", "J13", "N9", "O8", "M9",
                           "K9", "M11", "M12", "N11", "O11" }));
        multiStep.addTutorialStep(new InteractiveMoveTutorialStep("J11"));
        controller.addStep("Rules", multiStep);
        

        multiStep = new MultiTutorialStep();
        multiStep.addTutorialStep(new TextTutorialStep(
            "The second way to win at pente is to capture 5 pairs of your " +
            "opponents stones.  You can capture stones by placing one of " +
            "your stones on either side of 2 of your opponents stones. " +
            "Once captured, the stones are removed from the board.\n\n" +
            "Interactive - capture your opponents stones by playing " +
            "at O11.",
            "How to Win with Captures!"));
        multiStep.addTutorialStep(new MoveTutorialStep(
            new String[] { "K10", "L9", "N10", "N9", "M9", "L8", "L10" }));
        multiStep.addTutorialStep(new InteractiveMoveTutorialStep("O11"));
        controller.addStep("Rules", multiStep);

        multiStep = new MultiTutorialStep();
        multiStep.addTutorialStep(new TextTutorialStep(
            "You can make more than 1 capture at a time too.\n\n" +
            "Interactive - capture 2 pairs of your opponents stones.",
            "How to Win with Captures!"));
        multiStep.addTutorialStep(new MoveTutorialStep(
            new String[] { "K10", "J9", "K13", "J11", "H11", "J10", "J12", "J8",
                "J7", "G10", "M11", "H9", "F11", "G8", "K11", "F7", "E6", "K7",
                "L6", "K12", "K9", "G9", "G11", "H10", "H8", "H10", "K8", "H10",
                "J9", "J11" }));
        multiStep.addTutorialStep(new InteractiveMoveTutorialStep("H9"));
        controller.addStep("Rules", multiStep);

        multiStep = new MultiTutorialStep();
        multiStep.addTutorialStep(new TextTutorialStep(
            "One last clarification of captures, your own pieces can not " +
            "be captured if you play between 2 of your opponents stones.  " +
            "In the above position, the last move was made at M10 and was " +
            "not captured.",
            "Can't play into a capture"));
        multiStep.addTutorialStep(new MoveTutorialStep(
            new int[] { 180, 181, 183, 182 } ));
        controller.addStep("Rules", multiStep);

        TextTutorialStep text = new TextTutorialStep(
            "Those are all the rules for Pente.  The rules for Gomoku are even " +
            "simpler, in Gomoku there are no captures.  The only way to win " +
            "is by getting 5 of your stones in a row.", "Rules for Gomoku");
        text.setGame(GridStateFactory.GOMOKU);
        controller.addStep("Rules", text);

        multiStep = new MultiTutorialStep();
        multiStep.setGame(GridStateFactory.KERYO);
        multiStep.addTutorialStep(new TextTutorialStep(
            "Keryo-Pente is also very similar to pente with 1 additional rule. " +            "In Keryo-Pente you can capture your opponents stones like in " +            "Pente, but you can capture groups of 2 OR 3 stones at a time, " +            "instead of just groups of 2.  Because of this additional rule, in " +            "order to win by captures you must capture 15 of your opponents " +            "instead of 10 as in Pente.\n\n" +            "Interactive - Capture 3 of your opponents stones",
            "Rules for Keryo-Pente"));
        multiStep.addTutorialStep(new MoveTutorialStep(new String[] {
            "K10","L9","M6","J9","M9","G9","M7","H9","K9","F9","E9","M5","M8",
            "M10","K8","K7","K11","K12","N11","M10","J8","H7","N8","L10","K9",
            "O7","L8","N6","J8" }));
        multiStep.addTutorialStep(new InteractiveMoveTutorialStep("J10"));
        controller.addStep("Rules", multiStep);
        
        multiStep = new MultiTutorialStep();
        multiStep.setGame(GridStateFactory.GPENTE);
        multiStep.addTutorialStep(new TextTutorialStep(
            "G-Pente is a variation of Pente proposed by Gary Barnes " +            "that seeks to give player 2 more of a chance by imposing " +            "additional move restrictions for player 1's 2nd move.  In addition " +            "to the tournament rule, player 1 can not play straight out along " +            "any axis 3 or 4 moves out.  This means the following moves are " +            "illegal for player 1's 2nd move: K13, K14, N10, O10, K7, K6, G10, " +
            "and F10.  Other than that, all Pente rules apply.",
            "Rules for G-Pente"));
        MoveTutorialStep move = new MoveTutorialStep(false);
        move.addMoves(new String[] { "K10", "K13", "K14", "N10", "O10", "K7", "K6", "G10", "F10" }, 1);
        move.addMoves(new String[] { "L9" }, 2);
        multiStep.addTutorialStep(move);
        controller.addStep("Rules", multiStep);
        
        controller.addStep("Rules", new TextTutorialStep(
            "That's all the rules for the games at Pente.org.  However, to really have " +
            "fun you should learn some Pente strategy, a good next tutorial is " +
            "on the \"Basic Shapes\" of Pente.",
            "End of Tutorial"));
        
        
        controller.addSection("Basic Shapes");
        controller.addStep("Basic Shapes", new TextTutorialStep(
            "In Pente, you play with stones to make shapes and each shape is " +
            "either strong or weak. But let's start with the most basic shape\n\n" +
            "Author: Greg Strange (http://www.playpente.com/)",
            "Basic Shapes"));

        multiStep = new MultiTutorialStep();
        multiStep.addTutorialStep(new TextTutorialStep(
            "The single stone is the most elegant and only neutral shape. A " +
            "single stone can be neither strong nor weak. That is not to say " +
            "that playing a single stone is not strong or weak. So you learn " +
            "the first lesson: Every stone's strength or weakness depends on " +
            "the surrounding stones AND empty spaces. This idea shows the " +
            "Oriental roots of Pente. But I digress.",
            "Uni-Stone"));
        move = new MoveTutorialStep(false);
        move.addMoves(new String[] { "K10" }, 1);
        multiStep.addTutorialStep(move);
        controller.addStep("Basic Shapes", multiStep);
        
        multiStep = new MultiTutorialStep();
        multiStep.addTutorialStep(new TextTutorialStep(
            "The pair is extremely weak. Why? Because they can be captured, " +
            "the pair is the weakest possible shape. A pair is so weak that " +
            "until you get to the most advanced play, making a pair should be " +
            "avoided like the Black Death. In fact, it is your job to force " +
            "your opponent into making a pair to gain the upper hand and win " +
            "often. The second lesson: If you are not working on winning by " +
            "capturing pairs as well as making five in a row, you will only " +
            "be playing half of the game. It should be noted here that the " +
            "pair in some very rare and advanced circumstances can be " +
            "advantageous. For instance, if, by making a pair, you force your " +
            "opponent into making a move that will result in a win. This " +
            "position is often hard to see for the beginner. The beginner's " +
            "rule of thumb should be: Avoid pairs.",
            "Pair"));
        move = new MoveTutorialStep(false);
        move.addMoves(new String[] { "K10", "K11" }, 1);
        multiStep.addTutorialStep(move);
        controller.addStep("Basic Shapes", multiStep);

        multiStep = new MultiTutorialStep();
        multiStep.addTutorialStep(new TextTutorialStep(
            "The Stretch Two is a neutral to strong shape because it cannot " +
            "be captured and creates tension for your next move. In one move, " +
            "you will be able to make an open three and that will force your " +
            "opponent to respond to you (unless they have a greater threat on " +
            "the board like a four in a row or split four). Where possible, " +
            "you should make Stretch Twos to build your offense. It is from " +
            "Stretch Twos that all other strong shapes are formed.",
            "Stretch Two"));
        move = new MoveTutorialStep(false);
        move.addMoves(new String[] { "K10", "M10" }, 1);
        multiStep.addTutorialStep(move);
        controller.addStep("Basic Shapes", multiStep);

        multiStep = new MultiTutorialStep();
        multiStep.addTutorialStep(new TextTutorialStep(
            "Open threes are the building blocks of four in a row and " +
            "therefore are very strong. An open three (that is, three stones " +
            "in a row without any opponent's stones on either end) will lead " +
            "to an open four and certain win if not responded to. You can " +
            "see why an open three is very strong and how it can give you " +
            "momentum to defeat your opponent because in your next move you " +
            "can place four in a row or make a Stretch Four. More on Stretch " +
            "Fours in a minute.",
            "Open Three"));
        move = new MoveTutorialStep(false);
        move.addMoves(new String[] { "K10", "L10", "M10" }, 1);
        multiStep.addTutorialStep(move);
        controller.addStep("Basic Shapes", multiStep);

        multiStep = new MultiTutorialStep();
        multiStep.addTutorialStep(new TextTutorialStep(
            "Both shapes on the 2 line and the 4 line are Stretch Threes. " +
            "You can see that they are roughly equivalent to Open Threes " +
            "because, again, if unanswered, they will become open fours and " +
            "certain victory. So a Stretch Three must be answered by your " +
            "opponent which is a good thing. Stretch Threes, though, on the " +
            "whole, are weaker than open threes because an opponent can " +
            "place his stone in the middle of the Stretch Three and threaten " +
            "a capture (for instance at L10 or M8). This can be functionally " +
            "the same strength as a Stretch Two because you will lose " +
            "momentum but your opponent will not gain any momentum. Stretch " +
            "Threes are still more dangerous than Stretch Twos since they " +
            "can become a certain win in one more move. Not so with Stretch " +
            "Twos. Learning how to use Stretch Threes effectively is the " +
            "difference between an intermediate player and an advanced player.",
            "Stretch Three"));
        move = new MoveTutorialStep(false);
        move.addMoves(new String[] { "K10", "M10", "N10", "K8", "L8", "N8" }, 1);
        multiStep.addTutorialStep(move);
        controller.addStep("Basic Shapes", multiStep);
        
        multiStep = new MultiTutorialStep();
        multiStep.addTutorialStep(new TextTutorialStep(
            "Consider this board layout. This doesn't look like " +
            "much but with one stone...",            
            "Stretch Three"));
        move = new MoveTutorialStep(false);
        move.addMoves(new String[] { "K10", "K9", "N10", "M9" }, 1);
        multiStep.addTutorialStep(move);
        controller.addStep("Basic Shapes", multiStep);
        
        multiStep = new MultiTutorialStep();
        multiStep.addTutorialStep(new TextTutorialStep(
            "Consider this board layout. This doesn't look like " +
            "much but with one stone...\n\nYou have two Stretch Threes!\n\n" +
            "No matter where your opponent goes next, you will have an open " +
            "four and a certain victory. Pretty Slick!",
            "Stretch Three"));
        move = new MoveTutorialStep(true);
        move.addMoves(new String[] { "K10", "K9", "N10", "M9", "K7" }, 1);
        multiStep.addTutorialStep(move);
        controller.addStep("Basic Shapes", multiStep);

        multiStep = new MultiTutorialStep();
        multiStep.addTutorialStep(new TextTutorialStep(
            "This one is pretty obvious and doesn't need much explanation. " +
            "No matter what your opponent does in his next turn (except for " +
            "making a five in a row), you will win. If your opponent puts a " +
            "stone on either end of an open four, you simply put one on the " +
            "other end and you have five in a row and a win.",
            "Open Four"));
        move = new MoveTutorialStep(false);
        move.addMoves(new String[] { "K10", "L11", "M12", "N13" }, 1);
        multiStep.addTutorialStep(move);
        controller.addStep("Basic Shapes", multiStep);

        multiStep = new MultiTutorialStep();
        multiStep.addTutorialStep(new TextTutorialStep(
            "The Split Four is clearly a positive position in one respect " +
            "only, one move to five in a row. However, your opponent need " +
            "only place a stone right in the middle and now, in one move, " +
            "he will have a pair of your stones. Except for its immediacy in " +
            "terms of threatening your opponent with a win, the split four " +
            "should be avoided and usually is avoided in regular play.",
            "Split Four"));
        move = new MoveTutorialStep(false);
        move.addMoves(new String[] { "K10", "J10", "G10", "F10" }, 1);
        multiStep.addTutorialStep(move);
        controller.addStep("Basic Shapes", multiStep);

        multiStep = new MultiTutorialStep();
        multiStep.addTutorialStep(new TextTutorialStep(
            "Stretch Fours allow for some curious events to occur during a " +
            "normal game and their use marks an intermediate player. " +
            "Obviously, you need only one more stone to make a five in a row " +
            "and, even when your opponent places a stone in the middle of a " +
            "stretch four, you will not lose any pairs. In fact, when your " +
            "opponent places his stone in the middle (for instance, she " +
            "plays at K7 or M9), you have the option to place a stone at the " +
            "end of your three in a row and make a four in a row. A much " +
            "better situation! But there is another great aspect to the " +
            "Stretch Four.",
            "Stretch Four"));
        move = new MoveTutorialStep(false);
        move.addMoves(new String[] { "K10", "K9", "K8", "K6" }, 1);
        move.addMoves(new String[] { "M10", "M8", "M7", "M6" }, 1);
        multiStep.addTutorialStep(move);
        controller.addStep("Basic Shapes", multiStep);

        multiStep = new MultiTutorialStep();
        multiStep.addTutorialStep(new TextTutorialStep(
            "Consider the following layout.\n\n" +
            "Your opponent must respond to your open three on by playing at " +
            "either K6 or K10. Either of those plays will lead to a capture...",
            "Stretch Four"));
        move = new MoveTutorialStep(true);
        move.addMoves(new String[] { "J9", "J7" }, 2);
        move.addMoves(new String[] { "K9", "K8", "K7", "H8" }, 1);
        multiStep.addTutorialStep(move);
        controller.addStep("Basic Shapes", multiStep);

        multiStep = new MultiTutorialStep();
        multiStep.addTutorialStep(new TextTutorialStep(
            "Consider the following layout.\n\n" +
            "Your opponent must respond to your open three on by playing at " +
            "either K6 or K10. Either of those plays will lead to a capture..." +
            "\n\nBlack plays at K6...",
            "Stretch Four"));
        move = new MoveTutorialStep(true);
        move.addMoves(new String[] { "K9", "K8", "K7", "H8" }, 1);
        move.addMoves(new String[] { "J9", "J7", "K6" }, 2);
        multiStep.addTutorialStep(move);
        controller.addStep("Basic Shapes", multiStep);

        multiStep = new MultiTutorialStep();
        multiStep.addTutorialStep(new TextTutorialStep(
            "Consider the following layout.\n\n" +
            "Your opponent must respond to your open three on by playing at " +
            "either K6 or K10. Either of those plays will lead to a capture..." +
            "\n\nBlack plays at K6...White captures Black which forces Black " +
            "to stop the same open three...",
            "Stretch Four"));
        move = new MoveTutorialStep(true);
        move.addMoves(new String[] { "J9" }, 2);
        move.addMoves(new String[] { "K9", "K8", "K7", "H8", "L5" }, 1);
        multiStep.addTutorialStep(move);
        controller.addStep("Basic Shapes", multiStep);

        multiStep = new MultiTutorialStep();
        multiStep.addTutorialStep(new TextTutorialStep(
            "Black plays clever and places a stone again at K6 so as " +
            "to avoid another capture, but you will see he can't...",
            "Stretch Four"));
        move = new MoveTutorialStep(true);
        move.addMoves(new String[] { "K9", "K8", "K7", "H8", "L5" }, 1);
        move.addMoves(new String[] { "J9", "K6"}, 2);
        multiStep.addTutorialStep(move);
        controller.addStep("Basic Shapes", multiStep);

        multiStep = new MultiTutorialStep();
        multiStep.addTutorialStep(new TextTutorialStep(
            "Black plays clever and places a stone again at K6 so as " +
            "to avoid another capture, but you will see he can't...\n\n" +
            "White make a stretch four at K11...",
            "Stretch Four"));
        move = new MoveTutorialStep(true);
        move.addMoves(new String[] { "J9", "K6"}, 2);
        move.addMoves(new String[] { "K9", "K8", "K7", "H8", "L5", "K11" }, 1);
        multiStep.addTutorialStep(move);
        controller.addStep("Basic Shapes", multiStep);

        multiStep = new MultiTutorialStep();
        multiStep.addTutorialStep(new TextTutorialStep(
            "Black plays clever and places a stone again at K6 so as " +
            "to avoid another capture, but you will see he can't...\n\n" +
            "White make a stretch four at K11..." + "Black stops the five in " +
            "a row... ",
            "Stretch Four"));
        move = new MoveTutorialStep(true);
        move.addMoves(new String[] { "K9", "K8", "K7", "H8", "L5", "K11" }, 1);
        move.addMoves(new String[] { "J9", "K6", "K10" }, 2);
        multiStep.addTutorialStep(move);
        controller.addStep("Basic Shapes", multiStep);

        multiStep = new MultiTutorialStep();
        multiStep.addTutorialStep(new TextTutorialStep(
            "Black plays clever and places a stone again at K6 so as " +
            "to avoid another capture, but you will see he can't...\n\n" +
            "White make a stretch four at K11..." + "Black stops the five in " +
            "a row...White captures Black again opening up a five in a row " +
            "threat...",
            "Stretch Four"));
        move = new MoveTutorialStep(true);
        move.addMoves(new String[] { "K6", }, 2);
        move.addMoves(new String[] { "K9", "K8", "K7", "H8", "L5", "K11", "L11" }, 1);
        multiStep.addTutorialStep(move);
        controller.addStep("Basic Shapes", multiStep);

        multiStep = new MultiTutorialStep();
        multiStep.addTutorialStep(new TextTutorialStep(
            "Black plays clever and places a stone again at K6 so as " +
            "to avoid another capture, but you will see he can't...\n\n" +
            "White make a stretch four at K11..." + "Black stops the five in " +
            "a row...White captures Black again opening up a five in a row " +
            "threat...Black is forced to stop the five in a row again...",
            "Stretch Four"));
        move = new MoveTutorialStep(true);
        move.addMoves(new String[] { "K9", "K8", "K7", "H8", "L5", "K11", "L11" }, 1);
        move.addMoves(new String[] { "K6", "K10" }, 2);
        multiStep.addTutorialStep(move);
        controller.addStep("Basic Shapes", multiStep);

        multiStep = new MultiTutorialStep();
        multiStep.addTutorialStep(new TextTutorialStep(
            "Two captures on the same shape! Plus by playing at either J11, " +
            "M11 or J8, White can make an open three and then have a lot of " +
            "momentum to win. Stretch Fours are very strong shapes and " +
            "should be used as often as possible to win.",
            "Stretch Four"));
        move = new MoveTutorialStep(true);
        move.addMoves(new String[] { "K9", "K8", "K7", "H8", "L5", "K11", "L11" }, 1);
        move.addMoves(new String[] { "K6", "K10" }, 2);
        multiStep.addTutorialStep(move);
        controller.addStep("Basic Shapes", multiStep);


        multiStep = new MultiTutorialStep();
        multiStep.addTutorialStep(new TextTutorialStep(
            "Pente is the goal and goes without explanation. This is the " +
            "strongest of all shapes. There are Stretch Fives but they are " +
            "really variations on Stretch Fours with an added threat to be " +
            "captured instead of capturing. Stretch Fives are rare. But you " +
            "should be aware of one shape that happens every once in a " +
            "while and is extremely strong. I call it the Scorpion's Tail " +
            "because it will sting you if you don't watch out.",
            "Pente"));
        move = new MoveTutorialStep(false);
        move.addMoves(new String[] { "H10", "J10", "K10", "L10", "M10" }, 1);
        multiStep.addTutorialStep(move);
        controller.addStep("Basic Shapes", multiStep);

        multiStep = new MultiTutorialStep();
        multiStep.addTutorialStep(new TextTutorialStep(
            "This looks deceptive because it doesn't look like a win and it " +
            "isn't if your opponent counters correctly. There is only one " + 
            "correct response to this shape. The reason why this shape " +
            "is dangerous is because a stone at K10 means that you win in the " +
            "next move.",
            "Scorpion's Tail"));
        move = new MoveTutorialStep(false);
        move.addMoves(new String[] { "G10", "J10", "L10", "N10" }, 1);
        multiStep.addTutorialStep(move);
        controller.addStep("Basic Shapes", multiStep);

        multiStep = new MultiTutorialStep();
        multiStep.addTutorialStep(new TextTutorialStep(
            "Your opponent can place a stone at either H10 or M10 to block " +
            "but you can place your stone at the other opening for Pente. In " +
            "this way, you can see that the Scorpion's Tail is really two " +
            "overlapping Stretch Fours. The only correct response is for your" +
            " opponent to play at K10 first. This shows a general rule of " +
            "thumb in Pente. Wherever your opponent's best place to play is, " +
            "you should put your stone there.",
            "Scorpion's Tail"));
        move = new MoveTutorialStep(true);
        move.addMoves(new String[] { "G10", "J10", "L10", "N10", "K10", }, 1);
        multiStep.addTutorialStep(move);
        controller.addStep("Basic Shapes", multiStep);

        controller.addStep("Basic Shapes",
            new TextTutorialStep(
            "This completes the Basic Shapes tutorial, now start playing!",
            "End of Tutorial"));

        return controller;
    }

}
