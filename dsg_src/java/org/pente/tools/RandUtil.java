package org.pente.tools;

import java.io.*;
import java.util.*;

//64bit random values for use in zobrist hash function
public class RandUtil {
    public static void main(String[] args) throws IOException {
        long r[][] = readValues(args[0]);
    }

    public static long[][] readValues(String file) throws IOException {
        
        long r[][] = new long[2][361];
        DataInputStream in = new DataInputStream(new FileInputStream(file));

        long r2[]=new long[1460];
        for (int i = 0; i < 1460; i++) {
            r2[i]=in.readLong();
            System.out.println(r2[i] + "L,");
        }
        for (int i=0;i<1460;i++){
            for(int j=0;j<1460;j++){
                if (i==j)continue;
                if (r2[i]==r2[j]) System.out.println("equals ");
            }
        }
        
//        for (int i = 0; i < 2; i++) {
//            
//            for (int j = 0; j < 361; j++) {
//                r[i][j] = in.readLong();
//                System.out.println(r[i][j] + "L,");
//            }
//            System.out.println();
//        }
//        
        return r;
    }
}

// form go code i found online
//    long hashval = 0;
//
//    for (int i=0; i< stones.length; i++)
//    {
//        int x = stones[i].getX();
//        int y = stones[i].getY();
//
//        if (stones[i].getColor().equals(Color.BLACK))
//        {
//            hashval ^= blackRandom[x][y];
//        }
//        else if (stones[i].getColor().equals(Color.WHITE))
//        {
//            hashval ^= whiteRandom[x][y];
//        }
//        // Shouldn't be any Stone objects of color EMPTY,
//        // but if there are, they can be safely ignored.
//    }
//
//    Long l = new Long(hashval);
//
//    return l;
