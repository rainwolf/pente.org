package org.pente.tree;

import java.util.List;

public interface Node {

    public static final int TYPE_WIN = 3;
    public static final int TYPE_LOSE = 4;
    public static final int TYPE_UNKNOWN = 5;

    public Long getId();

    public void setId(Long id);

    public int getPlayer();

    public void setPlayer(int player);

    public int getPosition();

    public void setPosition(int position);

    public int getType();

    public void setType(int type);

    public String getComment();

    public void setComment(String comment);

    public long getHash();

    public void setHash(long hash);

    public int getDepth();

    public void setDepth(int depth);

    public int getRotation();

    public void setRotation(int rotation);

    public long getParentHash();

    public void setParentHash(long hash);

    public Node getParent();

    public void setParent(Node parent);

    public void addTwin(Node twin);

    public int getNumTwins();

    public List<Node> getTwins();

    public boolean isTwin();

    public void setTwin(boolean isTwin);

    public boolean isRoot();

    public void setNextMoves(List<Node> nextMoves);

    public List<Node> getNextMoves();

    public Node getNextMove(int position);

    public Node getNextMoveSafe(int position);

    public boolean nextMovesLoaded();

    /**
     * if find an existing node down a different path, add it as a child
     * but don't change the child's parent.
     */
    public void addExistingNextMove(Node n);

    public void addNextMove(Node n);

    public void removeNextMove(Node n);

    public void setPotentialNextMoves(List<Rank> p);

    public void clearPotentials();

    public int getNumPotentials();

    public Node getBestPotential();

    //public Node getBestPotential(Rank r);
    public Rank getBestRank();

    public boolean allNextMovesLose();

    public void setDefaultNextMove(Node afterDefault);

    public Node getDefaultNextMove();

    public boolean isStored();

    public void setStored(boolean stored);

    public boolean nodeNeedsWrite();

    public int getHeapIndex();

    public void setHeapIndex(int i);
}