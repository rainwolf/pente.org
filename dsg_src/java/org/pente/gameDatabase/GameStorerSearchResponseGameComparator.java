package org.pente.gameDatabase;

import java.util.*;

import org.pente.game.*;

public class GameStorerSearchResponseGameComparator implements Comparator {

    public GameStorerSearchResponseGameComparator() {
    }

    public int compare(Object obj1, Object obj2) {

        if (!(obj1 instanceof GameData) ||
                !(obj2 instanceof GameData)) {
            throw new IllegalArgumentException("Invalid objects");
        }

        int compareResult = 0;
        GameData gameData1 = (GameData) obj1;
        GameData gameData2 = (GameData) obj2;

        compareResult = -gameData1.getDate().compareTo(gameData2.getDate());

        return compareResult;
    }
}