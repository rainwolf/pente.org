package org.pente.gameDatabase.swing.importer;

import java.io.*;
import java.util.*;

import org.pente.game.*;
import org.pente.gameDatabase.swing.*;

public class MarksOpeningConverter {

    /**
     * @param args
     */
    public static void main(String[] args) throws Throwable {
        // TODO Auto-generated method stub

        File f = new File(args[0]);
        FileInputStream in = new FileInputStream(f);

        int obsize = getShort(in);

        Map<Long, PlunkNode> nodes = new HashMap<Long, PlunkNode>();
        GridState gs = GridStateFactory.createGridState(GridStateFactory.PENTE);
        PlunkNode root = null;

        int obi = 0;

        do {
            obi++;
            gs.clear();
            int numMoves = getShort(in);
            if (numMoves == -1) break;

            getShort(in);//unused ref number
            int score = getShort(in);


            int lm = 0;//last move, sometimes same move is repeated at end
            int moves[] = new int[numMoves];
            StringBuffer b = new StringBuffer();
            for (int j = 0; j < numMoves; j++) {
                int m = getShort(in);
                int y = m / 19;
                int x = m % 19;
                moves[j] = (18 - y) * 19 + x;
                b.append(moves[j] + ",");
            }
            System.out.println(b);


            //have list of moves for the new position
            //hash table of already scanned nodes
            //tree of already scanned nodes
            //for each move
            //    if root == null
            // special
            //    else
            //       load moves into grid state except for the new move
            //       rotate move using parent's rotation
            //       add move to grid state
            //       calculate new move and rotation

            for (int j = 0; j < numMoves; j++) {
                if (lm == moves[j]) break;

                gs.clear();

                if (root == null) {
                    PlunkNode e = new PlunkNode();
                    root = e;
                    e.setMove(moves[j]);
                    gs.addMove(moves[j]);
                    e.setHash(gs.getHash());
                    e.setDepth(j);
                    e.setRotation(gs.getRotation());
                    nodes.put(gs.getHash(), e);
                } else if (j > 0) {
                    for (int k = 0; k < j + 1; k++) {
                        gs.addMove(moves[k]);
                    }
                    if (nodes.get(gs.getHash()) != null) continue;

                    gs.undoMove();

                    PlunkNode p = nodes.get(gs.getHash());
                    PlunkNode e = new PlunkNode();
                    e.setParent(p);


                    GridState gs2 = GridStateFactory.createGridState(GridStateFactory.PENTE);
                    PlunkNode path[] = new PlunkNode[p.getDepth() + 1];
                    PlunkNode t = p;
                    while (t != null) {
                        path[t.getDepth()] = t;
                        t = t.getParent();
                    }
                    for (int i = 0; i < path.length; i++) {
                        gs2.addMove(path[i].getMove());
                    }

                    int rot = moves[j];
                    int possMoves[] = gs2.getAllPossibleRotations(moves[j],
                            gs.getRotation());
                    boolean foundSame = false;
                    for (int pm : possMoves) {
                        if (pm == moves[j]) {
                            foundSame = true;
                            break;
                        }
                    }
                    if (!foundSame) {
                        rot = gs2.rotateMoveToLocalRotation(
                                moves[j], gs.getRotation());
                    }


                    gs2.addMove(rot);
                    e.setMove(rot);
                    e.setHash(gs2.getHash());
                    e.setDepth(j);
                    e.setRotation(gs2.getRotation());

                    if (rot != moves[j]) {
                        System.out.println("rotated " + moves[j] + " to " + rot);

                    }
                    if (j == numMoves - 1) {
                        e.setComments("Score=" + score);
                    }

                    nodes.put(gs2.getHash(), e);
                }
                lm = moves[j];
            }

        } while (obi < obsize);

        in.close();

        PlunkTree pt = new PlunkTree();
        pt.setName("Mark Mammel's AI Opening Book");
        pt.setCreated(new Date());
        pt.setCreator("Mark Mammel");
        pt.setVersion("1.01");
        pt.setRoot(root);

        FileWriter out = new FileWriter(args[1]);
        SGFGameFormat sgf = new SGFGameFormat();
        sgf.format(root, 1, pt, out);

        out.close();
    }

    //need some trickery here to get the same thing the c code
    //does.
    private static int getShort(InputStream s) throws Exception {

        int i1 = s.read();
        if (i1 == -1) return -1;
        int i2 = s.read();
        if (i2 == -1) return -1;

        i2 = i2 << 8;
        i2 = i2 | i1;
        i2 = ((short) i2);

        return i2;
    }

}
