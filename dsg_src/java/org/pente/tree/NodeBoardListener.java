package org.pente.tree;

public interface NodeBoardListener {

    public void addMove(int position, int type);
    public void deleteMove(int position);
    
    public void toggleDefault();
    public void store();
    public void load();
    public void singleScan();
    public void scan(int maxDepth, int maxNodes);
    public void registerView(AWTNodeEditor editor, AWTBoard board);

    public void stop();
    public void visitNextScanned();
    public void visitPrevScanned();
    public void scanCompleted();
}
