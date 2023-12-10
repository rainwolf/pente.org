package org.pente.gameDatabase;

import java.io.*;
import java.util.*;
import java.util.zip.*;

import javax.servlet.http.*;

import org.pente.game.*;

public class ZipFileGameStorerSearchResponseStream {

    public ZipFileGameStorerSearchResponseStream() {
    }

    public void writeOutput(HttpServletResponse response,
                            GameStorerSearchResponseData responseData,
                            GameFormat gameFormat) throws Throwable {

        response.setContentType("application/zip");

        ZipOutputStream zipOutputStream = new ZipOutputStream(response.getOutputStream());
        zipOutputStream.setLevel(Deflater.BEST_COMPRESSION);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);

        Hashtable players = new Hashtable();

        Enumeration games = responseData.getGames().elements();
        while (games.hasMoreElements()) {
            GameData gameData = (GameData) games.nextElement();
            ZipEntry gameEntry = new ZipEntry("game/" + gameData.getGameID());
            zipOutputStream.putNextEntry(gameEntry);

            // write game file
            StringBuffer gameBuf = new StringBuffer();
            gameFormat.format(gameData, gameBuf);
            zipOutputStream.write(gameBuf.toString().getBytes());
            zipOutputStream.closeEntry();

            PlayerData player1Data = gameData.getPlayer1Data();
            // write player 1 file
            if (players.get(Long.valueOf(player1Data.getUserID())) == null) {
                ZipEntry player1Entry = new ZipEntry("player/" + player1Data.getUserID());
                zipOutputStream.putNextEntry(player1Entry);
                objectOutputStream.writeObject(player1Data);
                objectOutputStream.writeUTF(gameData.getSite());
                objectOutputStream.flush();
                zipOutputStream.write(byteArrayOutputStream.toByteArray());
                byteArrayOutputStream.reset();
                zipOutputStream.closeEntry();

                players.put(Long.valueOf(player1Data.getUserID()), "");
            }
            PlayerData player2Data = gameData.getPlayer2Data();
            if (players.get(Long.valueOf(player2Data.getUserID())) == null) {
                // write player 2 file
                ZipEntry player2Entry = new ZipEntry("player/" + player2Data.getUserID());
                zipOutputStream.putNextEntry(player2Entry);
                objectOutputStream.writeObject(player2Data);
                objectOutputStream.writeUTF(gameData.getSite());
                objectOutputStream.flush();
                zipOutputStream.write(byteArrayOutputStream.toByteArray());
                byteArrayOutputStream.reset();
                zipOutputStream.closeEntry();

                players.put(Long.valueOf(player2Data.getUserID()), "");
            }
        }

        objectOutputStream.close();
        zipOutputStream.close();
    }
}