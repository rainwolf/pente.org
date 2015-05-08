package org.pente.game;

import java.util.*;

public interface GameDbData {

    public void setID(int id);
    public int getID();

    public void setName(String name);
    public String getName();

    public void addGameTreeData(GameTreeData gameTreeData);
    public void addGameTreeData(Vector gameTreeData);
    public List<GameTreeData> getGameTreeData();

    public Object clone();
}
