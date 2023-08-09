/**
 * ClientTable.java
 * Copyright (C) 2001 Dweebo's Stone Games (http://www.pente.org/)
 * <p>
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, you can find it online at
 * http://www.gnu.org/copyleft/gpl.txt
 */

package org.pente.gameServer.client;

import org.pente.gameServer.event.*;

public class ClientTable implements DSGEventListener {

    private TableComponent tableComponent;
    private int tableNum;

    private volatile boolean destroyed;

    public ClientTable(TableComponent tableComponent, int tableNum) {
        this.tableComponent = tableComponent;
        this.tableNum = tableNum;
    }

    public void destroy() {
        destroyed = true;
    }

    public void eventOccurred(DSGEvent dsgEvent) {

        if (destroyed) return;

        if (dsgEvent instanceof DSGTableEvent) {
            DSGTableEvent dsgTableEvent = (DSGTableEvent) dsgEvent;
            if (dsgTableEvent.getTable() == tableNum) {

                if (dsgEvent instanceof DSGTextTableEvent) {
                    tableComponent.receiveText((DSGTextTableEvent) dsgEvent);
                } else if (dsgEvent instanceof DSGMoveTableEvent) {
                    tableComponent.receiveMove((DSGMoveTableEvent) dsgEvent);
                } else if (dsgEvent instanceof DSGTimerChangeTableEvent) {
                    tableComponent.receiveTimerChange((DSGTimerChangeTableEvent) dsgEvent);
                } else if (dsgEvent instanceof DSGJoinTableEvent) {
                    tableComponent.receivePlayerJoin((DSGJoinTableEvent) dsgEvent);
                } else if (dsgEvent instanceof DSGExitTableEvent) {
                    tableComponent.receivePlayerExit((DSGExitTableEvent) dsgEvent);
                } else if (dsgEvent instanceof DSGExitTableErrorEvent) {
                    tableComponent.receivePlayerExitError((DSGExitTableErrorEvent) dsgEvent);
                } else if (dsgEvent instanceof DSGSitTableEvent) {
                    tableComponent.receivePlayerSit((DSGSitTableEvent) dsgEvent);
                } else if (dsgEvent instanceof DSGStandTableEvent) {
                    tableComponent.receivePlayerStand((DSGStandTableEvent) dsgEvent);
                } else if (dsgEvent instanceof DSGStandTableErrorEvent) {
                    tableComponent.receivePlayerStandError((DSGStandTableErrorEvent) dsgEvent);
                } else if (dsgEvent instanceof DSGSwapSeatsTableEvent) {
                    tableComponent.receivePlayerSwap((DSGSwapSeatsTableEvent) dsgEvent);
                } else if (dsgEvent instanceof DSGSetPlayingPlayerTableEvent) {
                    tableComponent.receivePlayerPlaying((DSGSetPlayingPlayerTableEvent) dsgEvent);
                } else if (dsgEvent instanceof DSGOwnerTableEvent) {
                    tableComponent.receiveSetOwner(((DSGOwnerTableEvent) dsgEvent).getPlayer());
                } else if (dsgEvent instanceof DSGChangeStateTableEvent) {
                    tableComponent.changeTableState((DSGChangeStateTableEvent) dsgEvent);
                } else if (dsgEvent instanceof DSGGameStateTableEvent) {
                    tableComponent.setGameState((DSGGameStateTableEvent) dsgEvent);
                } else if (dsgEvent instanceof DSGUndoRequestTableEvent) {
                    tableComponent.receiveUndoRequest((DSGUndoRequestTableEvent) dsgEvent);
                } else if (dsgEvent instanceof DSGUndoReplyTableEvent) {
                    tableComponent.receiveUndoReply((DSGUndoReplyTableEvent) dsgEvent);
                } else if (dsgEvent instanceof DSGCancelRequestTableEvent) {
                    tableComponent.receiveCancelRequest((DSGCancelRequestTableEvent) dsgEvent);
                } else if (dsgEvent instanceof DSGCancelReplyTableEvent) {
                    tableComponent.receiveCancelReply((DSGCancelReplyTableEvent) dsgEvent);
                } else if (dsgEvent instanceof DSGWaitingPlayerReturnTimeUpTableEvent) {
                    tableComponent.receivePlayerReturnTimeUpEvent((DSGWaitingPlayerReturnTimeUpTableEvent) dsgEvent);
                } else if (dsgEvent instanceof DSGEmailGameReplyTableEvent) {
                    tableComponent.receiveEmailGameReply((DSGEmailGameReplyTableEvent) dsgEvent);
                } else if (dsgEvent instanceof DSGSystemMessageTableEvent) {
                    tableComponent.receiveSystemMessage((DSGSystemMessageTableEvent) dsgEvent);
                } else if (dsgEvent instanceof DSGPlayTableEvent) {
                    tableComponent.receivePlay((DSGPlayTableEvent) dsgEvent);
                } else if (dsgEvent instanceof DSGPlayTableErrorEvent) {
                    tableComponent.receivePlayError((DSGPlayTableErrorEvent) dsgEvent);
                } else if (dsgEvent instanceof DSGInviteResponseTableEvent) {
                    tableComponent.receiveInviteResponse((DSGInviteResponseTableEvent) dsgEvent);
                } else if (dsgEvent instanceof DSGStartSetTimerEvent) {
                    tableComponent.receiveStartSetTimerEvent((DSGStartSetTimerEvent) dsgEvent);
                } else if (dsgEvent instanceof DSGRejectGoStateEvent) {
                    tableComponent.receiveRejectGoStateEvent((DSGRejectGoStateEvent) dsgEvent);
                }

            }
        } else if (dsgEvent instanceof DSGJoinMainRoomEvent) {
            tableComponent.receivePlayerJoinMainRoom((DSGJoinMainRoomEvent) dsgEvent);
        } else if (dsgEvent instanceof DSGExitMainRoomEvent) {
            tableComponent.receivePlayerExitMainRoom((DSGExitMainRoomEvent) dsgEvent);
        }
    }
}

