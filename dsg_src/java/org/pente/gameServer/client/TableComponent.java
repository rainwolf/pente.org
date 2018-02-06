/** TableComponent.java
 *  Copyright (C) 2001 Dweebo's Stone Games (http://www.pente.org/)
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, you can find it online at
 *  http://www.gnu.org/copyleft/gpl.txt
 */

package org.pente.gameServer.client;

import org.pente.gameServer.event.*;

public interface TableComponent {

	public void changeTableState(DSGChangeStateTableEvent changeStateEvent);
	public void setGameState(DSGGameStateTableEvent stateEvent);
	public void receivePlayerSit(DSGSitTableEvent sitEvent);
	public void receivePlayerStand(DSGStandTableEvent standEvent);
	public void receivePlayerStandError(DSGStandTableErrorEvent standEvent);
    public void receivePlayerSwap(DSGSwapSeatsTableEvent swapEvent);
	public void receivePlayerJoin(DSGJoinTableEvent joinEvent);
	public void receivePlayerExit(DSGExitTableEvent exitEvent);
    public void receivePlayerExitError(DSGExitTableErrorEvent exitErrorEvent);
	public void receiveSetOwner(String player);
	public void receiveText(DSGTextTableEvent textEvent);
	public void receiveMove(DSGMoveTableEvent moveEvent);
	public void receiveUndoRequest(DSGUndoRequestTableEvent undoRequestEvent);
	public void receiveUndoReply(DSGUndoReplyTableEvent undoReplyEvent);
	public void receiveTimerChange(DSGTimerChangeTableEvent timerChangeEvent);
	public void receiveCancelRequest(DSGCancelRequestTableEvent cancelRequestEvent);
	public void receiveCancelReply(DSGCancelReplyTableEvent cancelReplyEvent);
	public void receivePlayerReturnTimeUpEvent(DSGWaitingPlayerReturnTimeUpTableEvent timeUpEvent);
    public void receiveEmailGameReply(DSGEmailGameReplyTableEvent emailGameReplyEvent);
    public void receivePlayerPlaying(DSGSetPlayingPlayerTableEvent setPlayingPlayerEvent);
    public void receiveSystemMessage(DSGSystemMessageTableEvent systemMessageEvent);
	public void receivePlay(DSGPlayTableEvent playEvent);
	public void receivePlayError(DSGPlayTableErrorEvent playErrorEvent);
	public void receiveInviteResponse(DSGInviteResponseTableEvent inviteResponseEvent);
	
    public void receivePlayerJoinMainRoom(DSGJoinMainRoomEvent joinEvent);
    public void receivePlayerExitMainRoom(DSGExitMainRoomEvent exitEvent);
    
    public void receiveStartSetTimerEvent(DSGStartSetTimerEvent timerEvent);
    
    public void receiveRejectGoStateEvent(DSGRejectGoStateEvent goStateEvent);
    
    public java.awt.Dimension getNewTableSizePref();
}

