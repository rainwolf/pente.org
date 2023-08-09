package org.pente.tools;

import java.io.*;

import org.pente.game.*;

public class ZobristHashConverter {
    //run this on all data in pente_move
//delete from pente_move, pente_game where we find invalid games
    public static void main(String[] args) throws IOException {
        BufferedReader in = new BufferedReader(new FileReader(args[0]));
        BufferedWriter out = new BufferedWriter(new FileWriter(args[1]));
        String line = null;

        long prevGid = -1;
        long wroteGid = -1;
        int moves[] = new int[361];
        moves[0] = 180;
        int moveNum = 1;
        while ((line = in.readLine()) != null) {

            String data[] = line.split("\t");
            //System.out.println(data[0] + ":" + data[1]);
            long gid = Long.parseLong(data[0]);

            if (prevGid == -1) {
                prevGid = gid;
            } else if (prevGid != gid) {
                if (wroteGid != prevGid) {
                    System.out.println(prevGid + " not written");
                }
                moves = new int[361];
                moves[0] = 180;
                moveNum = 1;
                prevGid = gid;
            }

            int nextMove = Integer.parseInt(data[2]);

            // end of game, calculate new hashes and output
            if (nextMove == 361) {
                int game = Integer.parseInt(data[5]);
                //if (game != 1) System.out.println("game="+game+",gid="+gid);
                String winner = data[6];
                //System.out.println("gid="+gid+",game="+game);
                GridState gs = GridStateFactory.createGridState(game);
                if (gs == null) {
                    System.out.println(gid + ": invalid game " + game);
                } else {
                    for (int i = 0; i < moveNum; i++) {
                        try {
                            gs.addMove(moves[i]);

                            int mv = (i == moveNum - 1) ? 361 : moves[i + 1];
                            String output = prevGid + "\t" + i + "\t" + mv + "\t" +
                                    gs.getHash(i) + "\t" + gs.getRotation(i) + "\t" +
                                    game + "\t" + winner + "\n";
                            out.write(output);
                        } catch (IllegalArgumentException iae) {
                            System.out.println(gid + ": " + iae.getMessage());
                        }
                    }
                }
                wroteGid = gid;
            } else {
                moves[moveNum++] = nextMove;
            }

        }

        in.close();
        out.close();
    }

}
