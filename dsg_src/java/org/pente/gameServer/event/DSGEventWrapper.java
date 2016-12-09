package org.pente.gameServer.event;

import com.google.gson.*;

import java.lang.reflect.Field;
import java.lang.reflect.Type;

import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.core.DSGPlayerGameData;
import org.pente.gameServer.core.SimpleDSGPlayerData;
import org.pente.gameServer.core.SimpleDSGPlayerGameData;

/**
 * Created by waliedothman on 27/11/2016.
 */
public class DSGEventWrapper {
    private DSGAddAITableEvent dsgAddAITableEvent;
    private DSGBootMainRoomEvent dsgBootMainRoomEvent;
    private DSGBootTableErrorEvent dsgBootTableErrorEvent;
    private DSGBootTableEvent dsgBootTableEvent;
    private DSGCancelReplyTableErrorEvent dsgCancelReplyTableErrorEvent;
    private DSGCancelReplyTableEvent dsgCancelReplyTableEvent;
    private DSGCancelRequestTableErrorEvent dsgCancelRequestTableErrorEvent;
    private DSGCancelRequestTableEvent dsgCancelRequestTableEvent;
    private DSGChangeStateTableErrorEvent dsgChangeStateTableErrorEvent;
    private DSGChangeStateTableEvent dsgChangeStateTableEvent;
    private DSGClientErrorEvent dsgClientErrorEvent;
    private DSGEmailGameReplyTableEvent dsgEmailGameReplyTableEvent;
    private DSGEmailGameRequestTableEvent dsgEmailGameRequestTableEvent;
    private DSGExitMainRoomEvent dsgExitMainRoomEvent;
    private DSGExitTableErrorEvent dsgExitTableErrorEvent;
    private DSGExitTableEvent dsgExitTableEvent;
    private DSGForceCancelResignTableErrorEvent dsgForceCancelResignTableErrorEvent;
    private DSGForceCancelResignTableEvent dsgForceCancelResignTableEvent;
    private DSGGameStateTableEvent dsgGameStateTableEvent;
    private DSGIgnoreEvent dsgIgnoreEvent;
    private DSGInviteResponseTableEvent dsgInviteResponseTableEvent;
    private DSGInviteTableErrorEvent dsgInviteTableErrorEvent;
    private DSGInviteTableEvent dsgInviteTableEvent;
    private DSGJoinMainRoomErrorEvent dsgJoinMainRoomErrorEvent;
    private DSGJoinMainRoomEvent dsgJoinMainRoomEvent;
    private DSGJoinTableErrorEvent dsgJoinTableErrorEvent;
    private DSGJoinTableEvent dsgJoinTableEvent;
    private DSGLoginErrorEvent dsgLoginErrorEvent;
    private DSGLoginEvent dsgLoginEvent;
    private DSGMainRoomErrorEvent dsgMainRoomErrorEvent;
    private DSGMainRoomEvent dsgMainRoomEvent;
    private DSGMoveTableErrorEvent dsgMoveTableErrorEvent;
    private DSGMoveTableEvent dsgMoveTableEvent;
    private DSGOwnerTableEvent dsgOwnerTableEvent;
    private DSGPingEvent dsgPingEvent;
    private DSGPlayTableErrorEvent dsgPlayTableErrorEvent;
    private DSGPlayTableEvent dsgPlayTableEvent;
    private DSGPreferenceEvent dsgPreferenceEvent;
    private DSGResignTableErrorEvent dsgResignTableErrorEvent;
    private DSGResignTableEvent dsgResignTableEvent;
    private DSGServerStatsEvent dsgServerStatsEvent;
    private DSGSetPlayingPlayerTableEvent dsgSetPlayingPlayerTableEvent;
    private DSGSitTableErrorEvent dsgSitTableErrorEvent;
    private DSGSitTableEvent dsgSitTableEvent;
    private DSGStandTableErrorEvent dsgStandTableErrorEvent;
    private DSGStandTableEvent dsgStandTableEvent;
    private DSGStartSetTimerEvent dsgStartSetTimerEvent;
    private DSGSwapSeatsTableEvent dsgSwapSeatsTableEvent;
    private DSGSystemMessageTableEvent dsgSystemMessageTableEvent;
    private DSGTableErrorEvent dsgTableErrorEvent;
    private DSGTableEvent dsgTableEvent;
    private DSGTextMainRoomErrorEvent dsgTextMainRoomErrorEvent;
    private DSGTextMainRoomEvent dsgTextMainRoomEvent;
    private DSGTextTableErrorEvent dsgTextTableErrorEvent;
    private DSGTextTableEvent dsgTextTableEvent;
    private DSGTimerChangeTableEvent dsgTimerChangeTableEvent;
    private DSGTimeUpTableEvent dsgTimeUpTableEvent;
    private DSGUndoReplyTableErrorEvent dsgUndoReplyTableErrorEvent;
    private DSGUndoReplyTableEvent dsgUndoReplyTableEvent;
    private DSGUndoRequestTableErrorEvent dsgUndoRequestTableErrorEvent;
    private DSGUndoRequestTableEvent dsgUndoRequestTableEvent;
    private DSGUpdatePlayerDataEvent dsgUpdatePlayerDataEvent;
    private DSGWaitingPlayerReturnTimeUpTableEvent dsgWaitingPlayerReturnTimeUpTableEvent;

    public DSGEventWrapper(Object o) {
        Field[] fields = DSGEventWrapper.class.getDeclaredFields();
        for(Field f : fields){
            f.setAccessible(true);
            if (o.getClass().getName().equals(f.getType().getName())) {
                try {
                    f.set(this, o);
                    break;
                } catch (IllegalAccessException e) {
                    System.out.println("DSGEventWrapper, IllegalAccessException: " + e);
                }
            }
        }
    }

    public Object getEncodedEvent() {
        Field[] fields = this.getClass().getDeclaredFields();
        for(Field f : fields){
            f.setAccessible(true);
            try {
                Object v = f.get(this);
                if (v != null) {
                    return v;
                }
            } catch (IllegalAccessException e) {
                System.out.println("DSGEventWrapper, IllegalAccessException: " + e);
            }
        }
        return null;
    }

    public String getJSON() {
        return (new Gson()).toJson(this);
    }




    public DSGAddAITableEvent getDsgAddAITableEvent() {
        return dsgAddAITableEvent;
    }

    public void setDsgAddAITableEvent(DSGAddAITableEvent dsgAddAITableEvent) {
        this.dsgAddAITableEvent = dsgAddAITableEvent;
    }

    public DSGBootMainRoomEvent getDsgBootMainRoomEvent() {
        return dsgBootMainRoomEvent;
    }

    public void setDsgBootMainRoomEvent(DSGBootMainRoomEvent dsgBootMainRoomEvent) {
        this.dsgBootMainRoomEvent = dsgBootMainRoomEvent;
    }

    public DSGBootTableErrorEvent getDsgBootTableErrorEvent() {
        return dsgBootTableErrorEvent;
    }

    public void setDsgBootTableErrorEvent(DSGBootTableErrorEvent dsgBootTableErrorEvent) {
        this.dsgBootTableErrorEvent = dsgBootTableErrorEvent;
    }

    public DSGBootTableEvent getDsgBootTableEvent() {
        return dsgBootTableEvent;
    }

    public void setDsgBootTableEvent(DSGBootTableEvent dsgBootTableEvent) {
        this.dsgBootTableEvent = dsgBootTableEvent;
    }

    public DSGCancelReplyTableErrorEvent getDsgCancelReplyTableErrorEvent() {
        return dsgCancelReplyTableErrorEvent;
    }

    public void setDsgCancelReplyTableErrorEvent(DSGCancelReplyTableErrorEvent dsgCancelReplyTableErrorEvent) {
        this.dsgCancelReplyTableErrorEvent = dsgCancelReplyTableErrorEvent;
    }

    public DSGCancelReplyTableEvent getDsgCancelReplyTableEvent() {
        return dsgCancelReplyTableEvent;
    }

    public void setDsgCancelReplyTableEvent(DSGCancelReplyTableEvent dsgCancelReplyTableEvent) {
        this.dsgCancelReplyTableEvent = dsgCancelReplyTableEvent;
    }

    public DSGCancelRequestTableErrorEvent getDsgCancelRequestTableErrorEvent() {
        return dsgCancelRequestTableErrorEvent;
    }

    public void setDsgCancelRequestTableErrorEvent(DSGCancelRequestTableErrorEvent dsgCancelRequestTableErrorEvent) {
        this.dsgCancelRequestTableErrorEvent = dsgCancelRequestTableErrorEvent;
    }

    public DSGCancelRequestTableEvent getDsgCancelRequestTableEvent() {
        return dsgCancelRequestTableEvent;
    }

    public void setDsgCancelRequestTableEvent(DSGCancelRequestTableEvent dsgCancelRequestTableEvent) {
        this.dsgCancelRequestTableEvent = dsgCancelRequestTableEvent;
    }

    public DSGChangeStateTableErrorEvent getDsgChangeStateTableErrorEvent() {
        return dsgChangeStateTableErrorEvent;
    }

    public void setDsgChangeStateTableErrorEvent(DSGChangeStateTableErrorEvent dsgChangeStateTableErrorEvent) {
        this.dsgChangeStateTableErrorEvent = dsgChangeStateTableErrorEvent;
    }

    public DSGChangeStateTableEvent getDsgChangeStateTableEvent() {
        return dsgChangeStateTableEvent;
    }

    public void setDsgChangeStateTableEvent(DSGChangeStateTableEvent dsgChangeStateTableEvent) {
        this.dsgChangeStateTableEvent = dsgChangeStateTableEvent;
    }

    public DSGClientErrorEvent getDsgClientErrorEvent() {
        return dsgClientErrorEvent;
    }

    public void setDsgClientErrorEvent(DSGClientErrorEvent dsgClientErrorEvent) {
        this.dsgClientErrorEvent = dsgClientErrorEvent;
    }

    public DSGEmailGameReplyTableEvent getDsgEmailGameReplyTableEvent() {
        return dsgEmailGameReplyTableEvent;
    }

    public void setDsgEmailGameReplyTableEvent(DSGEmailGameReplyTableEvent dsgEmailGameReplyTableEvent) {
        this.dsgEmailGameReplyTableEvent = dsgEmailGameReplyTableEvent;
    }

    public DSGEmailGameRequestTableEvent getDsgEmailGameRequestTableEvent() {
        return dsgEmailGameRequestTableEvent;
    }

    public void setDsgEmailGameRequestTableEvent(DSGEmailGameRequestTableEvent dsgEmailGameRequestTableEvent) {
        this.dsgEmailGameRequestTableEvent = dsgEmailGameRequestTableEvent;
    }

    public DSGExitMainRoomEvent getDsgExitMainRoomEvent() {
        return dsgExitMainRoomEvent;
    }

    public void setDsgExitMainRoomEvent(DSGExitMainRoomEvent dsgExitMainRoomEvent) {
        this.dsgExitMainRoomEvent = dsgExitMainRoomEvent;
    }

    public DSGExitTableErrorEvent getDsgExitTableErrorEvent() {
        return dsgExitTableErrorEvent;
    }

    public void setDsgExitTableErrorEvent(DSGExitTableErrorEvent dsgExitTableErrorEvent) {
        this.dsgExitTableErrorEvent = dsgExitTableErrorEvent;
    }

    public DSGExitTableEvent getDsgExitTableEvent() {
        return dsgExitTableEvent;
    }

    public void setDsgExitTableEvent(DSGExitTableEvent dsgExitTableEvent) {
        this.dsgExitTableEvent = dsgExitTableEvent;
    }

    public DSGForceCancelResignTableErrorEvent getDsgForceCancelResignTableErrorEvent() {
        return dsgForceCancelResignTableErrorEvent;
    }

    public void setDsgForceCancelResignTableErrorEvent(DSGForceCancelResignTableErrorEvent dsgForceCancelResignTableErrorEvent) {
        this.dsgForceCancelResignTableErrorEvent = dsgForceCancelResignTableErrorEvent;
    }

    public DSGForceCancelResignTableEvent getDsgForceCancelResignTableEvent() {
        return dsgForceCancelResignTableEvent;
    }

    public void setDsgForceCancelResignTableEvent(DSGForceCancelResignTableEvent dsgForceCancelResignTableEvent) {
        this.dsgForceCancelResignTableEvent = dsgForceCancelResignTableEvent;
    }

    public DSGGameStateTableEvent getDsgGameStateTableEvent() {
        return dsgGameStateTableEvent;
    }

    public void setDsgGameStateTableEvent(DSGGameStateTableEvent dsgGameStateTableEvent) {
        this.dsgGameStateTableEvent = dsgGameStateTableEvent;
    }

    public DSGIgnoreEvent getDsgIgnoreEvent() {
        return dsgIgnoreEvent;
    }

    public void setDsgIgnoreEvent(DSGIgnoreEvent dsgIgnoreEvent) {
        this.dsgIgnoreEvent = dsgIgnoreEvent;
    }

    public DSGInviteResponseTableEvent getDsgInviteResponseTableEvent() {
        return dsgInviteResponseTableEvent;
    }

    public void setDsgInviteResponseTableEvent(DSGInviteResponseTableEvent dsgInviteResponseTableEvent) {
        this.dsgInviteResponseTableEvent = dsgInviteResponseTableEvent;
    }

    public DSGInviteTableErrorEvent getDsgInviteTableErrorEvent() {
        return dsgInviteTableErrorEvent;
    }

    public void setDsgInviteTableErrorEvent(DSGInviteTableErrorEvent dsgInviteTableErrorEvent) {
        this.dsgInviteTableErrorEvent = dsgInviteTableErrorEvent;
    }

    public DSGInviteTableEvent getDsgInviteTableEvent() {
        return dsgInviteTableEvent;
    }

    public void setDsgInviteTableEvent(DSGInviteTableEvent dsgInviteTableEvent) {
        this.dsgInviteTableEvent = dsgInviteTableEvent;
    }

    public DSGJoinMainRoomErrorEvent getDsgJoinMainRoomErrorEvent() {
        return dsgJoinMainRoomErrorEvent;
    }

    public void setDsgJoinMainRoomErrorEvent(DSGJoinMainRoomErrorEvent dsgJoinMainRoomErrorEvent) {
        this.dsgJoinMainRoomErrorEvent = dsgJoinMainRoomErrorEvent;
    }

    public DSGJoinMainRoomEvent getDsgJoinMainRoomEvent() {
        return dsgJoinMainRoomEvent;
    }

    public void setDsgJoinMainRoomEvent(DSGJoinMainRoomEvent dsgJoinMainRoomEvent) {
        this.dsgJoinMainRoomEvent = dsgJoinMainRoomEvent;
    }

    public DSGJoinTableErrorEvent getDsgJoinTableErrorEvent() {
        return dsgJoinTableErrorEvent;
    }

    public void setDsgJoinTableErrorEvent(DSGJoinTableErrorEvent dsgJoinTableErrorEvent) {
        this.dsgJoinTableErrorEvent = dsgJoinTableErrorEvent;
    }

    public DSGJoinTableEvent getDsgJoinTableEvent() {
        return dsgJoinTableEvent;
    }

    public void setDsgJoinTableEvent(DSGJoinTableEvent dsgJoinTableEvent) {
        this.dsgJoinTableEvent = dsgJoinTableEvent;
    }

    public DSGLoginErrorEvent getDsgLoginErrorEvent() {
        return dsgLoginErrorEvent;
    }

    public void setDsgLoginErrorEvent(DSGLoginErrorEvent dsgLoginErrorEvent) {
        this.dsgLoginErrorEvent = dsgLoginErrorEvent;
    }

    public DSGLoginEvent getDsgLoginEvent() {
        return dsgLoginEvent;
    }

    public void setDsgLoginEvent(DSGLoginEvent dsgLoginEvent) {
        this.dsgLoginEvent = dsgLoginEvent;
    }

    public DSGMainRoomErrorEvent getDsgMainRoomErrorEvent() {
        return dsgMainRoomErrorEvent;
    }

    public void setDsgMainRoomErrorEvent(DSGMainRoomErrorEvent dsgMainRoomErrorEvent) {
        this.dsgMainRoomErrorEvent = dsgMainRoomErrorEvent;
    }

    public DSGMainRoomEvent getDsgMainRoomEvent() {
        return dsgMainRoomEvent;
    }

    public void setDsgMainRoomEvent(DSGMainRoomEvent dsgMainRoomEvent) {
        this.dsgMainRoomEvent = dsgMainRoomEvent;
    }

    public DSGMoveTableErrorEvent getDsgMoveTableErrorEvent() {
        return dsgMoveTableErrorEvent;
    }

    public void setDsgMoveTableErrorEvent(DSGMoveTableErrorEvent dsgMoveTableErrorEvent) {
        this.dsgMoveTableErrorEvent = dsgMoveTableErrorEvent;
    }

    public DSGMoveTableEvent getDsgMoveTableEvent() {
        return dsgMoveTableEvent;
    }

    public void setDsgMoveTableEvent(DSGMoveTableEvent dsgMoveTableEvent) {
        this.dsgMoveTableEvent = dsgMoveTableEvent;
    }

    public DSGOwnerTableEvent getDsgOwnerTableEvent() {
        return dsgOwnerTableEvent;
    }

    public void setDsgOwnerTableEvent(DSGOwnerTableEvent dsgOwnerTableEvent) {
        this.dsgOwnerTableEvent = dsgOwnerTableEvent;
    }

    public DSGPingEvent getDsgPingEvent() {
        return dsgPingEvent;
    }

    public void setDsgPingEvent(DSGPingEvent dsgPingEvent) {
        this.dsgPingEvent = dsgPingEvent;
    }

    public DSGPlayTableErrorEvent getDsgPlayTableErrorEvent() {
        return dsgPlayTableErrorEvent;
    }

    public void setDsgPlayTableErrorEvent(DSGPlayTableErrorEvent dsgPlayTableErrorEvent) {
        this.dsgPlayTableErrorEvent = dsgPlayTableErrorEvent;
    }

    public DSGPlayTableEvent getDsgPlayTableEvent() {
        return dsgPlayTableEvent;
    }

    public void setDsgPlayTableEvent(DSGPlayTableEvent dsgPlayTableEvent) {
        this.dsgPlayTableEvent = dsgPlayTableEvent;
    }

    public DSGPreferenceEvent getDsgPreferenceEvent() {
        return dsgPreferenceEvent;
    }

    public void setDsgPreferenceEvent(DSGPreferenceEvent dsgPreferenceEvent) {
        this.dsgPreferenceEvent = dsgPreferenceEvent;
    }

    public DSGResignTableErrorEvent getDsgResignTableErrorEvent() {
        return dsgResignTableErrorEvent;
    }

    public void setDsgResignTableErrorEvent(DSGResignTableErrorEvent dsgResignTableErrorEvent) {
        this.dsgResignTableErrorEvent = dsgResignTableErrorEvent;
    }

    public DSGResignTableEvent getDsgResignTableEvent() {
        return dsgResignTableEvent;
    }

    public void setDsgResignTableEvent(DSGResignTableEvent dsgResignTableEvent) {
        this.dsgResignTableEvent = dsgResignTableEvent;
    }

    public DSGServerStatsEvent getDsgServerStatsEvent() {
        return dsgServerStatsEvent;
    }

    public void setDsgServerStatsEvent(DSGServerStatsEvent dsgServerStatsEvent) {
        this.dsgServerStatsEvent = dsgServerStatsEvent;
    }

    public DSGSetPlayingPlayerTableEvent getDsgSetPlayingPlayerTableEvent() {
        return dsgSetPlayingPlayerTableEvent;
    }

    public void setDsgSetPlayingPlayerTableEvent(DSGSetPlayingPlayerTableEvent dsgSetPlayingPlayerTableEvent) {
        this.dsgSetPlayingPlayerTableEvent = dsgSetPlayingPlayerTableEvent;
    }

    public DSGSitTableErrorEvent getDsgSitTableErrorEvent() {
        return dsgSitTableErrorEvent;
    }

    public void setDsgSitTableErrorEvent(DSGSitTableErrorEvent dsgSitTableErrorEvent) {
        this.dsgSitTableErrorEvent = dsgSitTableErrorEvent;
    }

    public DSGSitTableEvent getDsgSitTableEvent() {
        return dsgSitTableEvent;
    }

    public void setDsgSitTableEvent(DSGSitTableEvent dsgSitTableEvent) {
        this.dsgSitTableEvent = dsgSitTableEvent;
    }

    public DSGStandTableErrorEvent getDsgStandTableErrorEvent() {
        return dsgStandTableErrorEvent;
    }

    public void setDsgStandTableErrorEvent(DSGStandTableErrorEvent dsgStandTableErrorEvent) {
        this.dsgStandTableErrorEvent = dsgStandTableErrorEvent;
    }

    public DSGStandTableEvent getDsgStandTableEvent() {
        return dsgStandTableEvent;
    }

    public void setDsgStandTableEvent(DSGStandTableEvent dsgStandTableEvent) {
        this.dsgStandTableEvent = dsgStandTableEvent;
    }

    public DSGStartSetTimerEvent getDsgStartSetTimerEvent() {
        return dsgStartSetTimerEvent;
    }

    public void setDsgStartSetTimerEvent(DSGStartSetTimerEvent dsgStartSetTimerEvent) {
        this.dsgStartSetTimerEvent = dsgStartSetTimerEvent;
    }

    public DSGSwapSeatsTableEvent getDsgSwapSeatsTableEvent() {
        return dsgSwapSeatsTableEvent;
    }

    public void setDsgSwapSeatsTableEvent(DSGSwapSeatsTableEvent dsgSwapSeatsTableEvent) {
        this.dsgSwapSeatsTableEvent = dsgSwapSeatsTableEvent;
    }

    public DSGSystemMessageTableEvent getDsgSystemMessageTableEvent() {
        return dsgSystemMessageTableEvent;
    }

    public void setDsgSystemMessageTableEvent(DSGSystemMessageTableEvent dsgSystemMessageTableEvent) {
        this.dsgSystemMessageTableEvent = dsgSystemMessageTableEvent;
    }

    public DSGTableErrorEvent getDsgTableErrorEvent() {
        return dsgTableErrorEvent;
    }

    public void setDsgTableErrorEvent(DSGTableErrorEvent dsgTableErrorEvent) {
        this.dsgTableErrorEvent = dsgTableErrorEvent;
    }

    public DSGTableEvent getDsgTableEvent() {
        return dsgTableEvent;
    }

    public void setDsgTableEvent(DSGTableEvent dsgTableEvent) {
        this.dsgTableEvent = dsgTableEvent;
    }

    public DSGTextMainRoomErrorEvent getDsgTextMainRoomErrorEvent() {
        return dsgTextMainRoomErrorEvent;
    }

    public void setDsgTextMainRoomErrorEvent(DSGTextMainRoomErrorEvent dsgTextMainRoomErrorEvent) {
        this.dsgTextMainRoomErrorEvent = dsgTextMainRoomErrorEvent;
    }

    public DSGTextMainRoomEvent getDsgTextMainRoomEvent() {
        return dsgTextMainRoomEvent;
    }

    public void setDsgTextMainRoomEvent(DSGTextMainRoomEvent dsgTextMainRoomEvent) {
        this.dsgTextMainRoomEvent = dsgTextMainRoomEvent;
    }

    public DSGTextTableErrorEvent getDsgTextTableErrorEvent() {
        return dsgTextTableErrorEvent;
    }

    public void setDsgTextTableErrorEvent(DSGTextTableErrorEvent dsgTextTableErrorEvent) {
        this.dsgTextTableErrorEvent = dsgTextTableErrorEvent;
    }

    public DSGTextTableEvent getDsgTextTableEvent() {
        return dsgTextTableEvent;
    }

    public void setDsgTextTableEvent(DSGTextTableEvent dsgTextTableEvent) {
        this.dsgTextTableEvent = dsgTextTableEvent;
    }

    public DSGTimerChangeTableEvent getDsgTimerChangeTableEvent() {
        return dsgTimerChangeTableEvent;
    }

    public void setDsgTimerChangeTableEvent(DSGTimerChangeTableEvent dsgTimerChangeTableEvent) {
        this.dsgTimerChangeTableEvent = dsgTimerChangeTableEvent;
    }

    public DSGTimeUpTableEvent getDsgTimeUpTableEvent() {
        return dsgTimeUpTableEvent;
    }

    public void setDsgTimeUpTableEvent(DSGTimeUpTableEvent dsgTimeUpTableEvent) {
        this.dsgTimeUpTableEvent = dsgTimeUpTableEvent;
    }

    public DSGUndoReplyTableErrorEvent getDsgUndoReplyTableErrorEvent() {
        return dsgUndoReplyTableErrorEvent;
    }

    public void setDsgUndoReplyTableErrorEvent(DSGUndoReplyTableErrorEvent dsgUndoReplyTableErrorEvent) {
        this.dsgUndoReplyTableErrorEvent = dsgUndoReplyTableErrorEvent;
    }

    public DSGUndoReplyTableEvent getDsgUndoReplyTableEvent() {
        return dsgUndoReplyTableEvent;
    }

    public void setDsgUndoReplyTableEvent(DSGUndoReplyTableEvent dsgUndoReplyTableEvent) {
        this.dsgUndoReplyTableEvent = dsgUndoReplyTableEvent;
    }

    public DSGUndoRequestTableErrorEvent getDsgUndoRequestTableErrorEvent() {
        return dsgUndoRequestTableErrorEvent;
    }

    public void setDsgUndoRequestTableErrorEvent(DSGUndoRequestTableErrorEvent dsgUndoRequestTableErrorEvent) {
        this.dsgUndoRequestTableErrorEvent = dsgUndoRequestTableErrorEvent;
    }

    public DSGUndoRequestTableEvent getDsgUndoRequestTableEvent() {
        return dsgUndoRequestTableEvent;
    }

    public void setDsgUndoRequestTableEvent(DSGUndoRequestTableEvent dsgUndoRequestTableEvent) {
        this.dsgUndoRequestTableEvent = dsgUndoRequestTableEvent;
    }

    public DSGUpdatePlayerDataEvent getDsgUpdatePlayerDataEvent() {
        return dsgUpdatePlayerDataEvent;
    }

    public void setDsgUpdatePlayerDataEvent(DSGUpdatePlayerDataEvent dsgUpdatePlayerDataEvent) {
        this.dsgUpdatePlayerDataEvent = dsgUpdatePlayerDataEvent;
    }

    public DSGWaitingPlayerReturnTimeUpTableEvent getDsgWaitingPlayerReturnTimeUpTableEvent() {
        return dsgWaitingPlayerReturnTimeUpTableEvent;
    }

    public void setDsgWaitingPlayerReturnTimeUpTableEvent(DSGWaitingPlayerReturnTimeUpTableEvent dsgWaitingPlayerReturnTimeUpTableEvent) {
        this.dsgWaitingPlayerReturnTimeUpTableEvent = dsgWaitingPlayerReturnTimeUpTableEvent;
    }
}


