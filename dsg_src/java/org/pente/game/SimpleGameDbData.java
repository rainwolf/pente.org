package org.pente.game;

import java.util.*;

public class SimpleGameDbData implements GameDbData, Cloneable, java.io.Serializable {

    private int id;
    private String name;
    private List<GameTreeData> games = new ArrayList<>(3);

    public void setID(int id) {
        this.id = id;
    }

    public int getID() {
        return id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String toString() {
        return name;
    }

    public void addGameTreeData(Vector<GameTreeData> gameTreeData) {
        games.addAll(gameTreeData);
    }

    public void addGameTreeData(GameTreeData gameTreeData) {
        games.add(gameTreeData);
    }


    public List<GameTreeData> getGameTreeData() {
        return games;
    }

    public Object clone() {

        SimpleGameDbData cloned = new SimpleGameDbData();

        cloned.setID(getID());
        cloned.setName(getName());

        for (int i = 0; i < games.size(); i++) {
            GameTreeData s = games.get(i);
            cloned.addGameTreeData((GameTreeData) s.clone());
        }

        return cloned;
    }
}
