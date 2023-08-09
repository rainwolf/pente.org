package org.pente.gameServer.server.test;

import org.pente.game.*;
import org.pente.gameServer.server.*;

import org.apache.log4j.*;

public class TestAI {


    public static void main(String[] args) throws Throwable {

        BasicConfigurator.configure();

        MarksAIPlayer player = new MarksAIPlayer();
        player.setGame(GridStateFactory.PENTE);
        player.setLevel(8);
        player.setSeat(2);
        player.setOption("configDirectory", "/dsg_src/conf/marksAI");
        player.init();

        player.addMove(180);
        player.addMove(182);
        player.addMove(183);
        player.addMove(218);
        player.addMove(145);
        player.addMove(221);
        player.addMove(162);

        System.out.println("start thinking...");
        long startTime = System.currentTimeMillis();
        int move = player.getMove();
        long time = System.currentTimeMillis() - startTime;
        System.out.println("move=" + move + " in " + time + " millis.");
    }

}
