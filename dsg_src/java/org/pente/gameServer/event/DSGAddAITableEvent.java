package org.pente.gameServer.event;

import org.pente.gameServer.core.AIData;

public class DSGAddAITableEvent extends AbstractDSGTableEvent {
    private static final long serialVersionUID = -760216353572540197L;

    private AIData aiData;

    public DSGAddAITableEvent() {
        super();
    }

    public DSGAddAITableEvent(String player, int table, AIData aiData) {
        super(player, table);

        this.aiData = aiData;
    }

    public AIData getAIData() {
        return aiData;
    }

    public String toString() {
        return "add ai " + aiData + " " + super.toString();
    }
}
