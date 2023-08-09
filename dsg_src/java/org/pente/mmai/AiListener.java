package org.pente.mmai;

public interface AiListener {

    public void startThinking();

    public void stopThinking();

    public void moveReady(int moves[], int newMove);

    public void aiEvaluateCallBack();

    public void aiVisualizationCallBack(int bd[]);
}
