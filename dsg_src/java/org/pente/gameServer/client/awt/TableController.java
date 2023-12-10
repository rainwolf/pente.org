package org.pente.gameServer.client.awt;

import java.awt.*;
import java.applet.*;
import java.util.*;

import org.pente.gameServer.core.DSGPlayerData;
import org.pente.gameServer.client.*;
import org.pente.gameServer.event.*;

public class TableController implements DSGEventListener {

    class TableData {
        Vector players = new Vector(); // of DSGPlayerData
        String sittingPlayers[] = new String[3];
        ClientTable clientTable;
        Frame frame;
    }

    private Hashtable tables = new Hashtable();

    private String host;
    private DSGPlayerData me;
    private GameStyles gameStyle;
    private DSGEventSource dsgEventSource;
    private DSGEventListener dsgEventListener;
    private Sounds sounds;

    private Vector aiDataVector;
    private PlayerListComponent mainRoomPlayerList;
    private PlayerDataCache playerDataCache;
    private PreferenceHandler preferenceHandler;

    public TableController(String host,
                           DSGPlayerData me,
                           GameStyles gameStyle,
                           DSGEventSource dsgEventSource,
                           DSGEventListener dsgEventListener,
                           Sounds sounds,
                           Vector aiDataVector,
                           PlayerListComponent mainRoomPlayerList,
                           PlayerDataCache playerDataCache,
                           PreferenceHandler preferenceHandler) {

        this.host = host;
        this.me = me;
        this.gameStyle = gameStyle;
        this.dsgEventSource = dsgEventSource;
        this.dsgEventListener = dsgEventListener;
        this.sounds = sounds;
        this.aiDataVector = aiDataVector;
        this.mainRoomPlayerList = mainRoomPlayerList;
        this.playerDataCache = playerDataCache;
        this.preferenceHandler = preferenceHandler;

        dsgEventSource.addListener(this);
    }

    public Frame getActiveTableFrame() {
        for (Enumeration e = tables.elements(); e.hasMoreElements(); ) {
            TableData d = (TableData) e.nextElement();
            if (d.frame != null && d.frame.isVisible()) {
                return d.frame;
            }
        }

        return null;
    }

    private TableData getTableData(int tableNum) {
        TableData data = (TableData) tables.get(Integer.valueOf(tableNum));
        if (data == null) {
            data = new TableData();
            tables.put(Integer.valueOf(tableNum), data);
        }
        return data;
    }

    public void eventOccurred(DSGEvent dsgEvent) {

        if (dsgEvent instanceof DSGJoinTableEvent) {

            DSGJoinTableEvent joinEvent = (DSGJoinTableEvent) dsgEvent;

            TableData data = getTableData(joinEvent.getTable());
            if (joinEvent.getPlayer().equals(me.getName())) {
                data.frame = new GameBoardFrame(
                        host,
                        gameStyle,
                        joinEvent.getTable(),
                        dsgEventListener,
                        me,
                        data.players,
                        data.sittingPlayers,
                        sounds,
                        aiDataVector,
                        mainRoomPlayerList,
                        playerDataCache,
                        preferenceHandler);

                data.clientTable = new ClientTable((TableComponent)
                        data.frame, joinEvent.getTable());
                dsgEventSource.addListener(data.clientTable);
            }
            data.players.addElement(playerDataCache.getPlayer(joinEvent.getPlayer()));
        } else if (dsgEvent instanceof DSGExitTableEvent) {
            DSGExitTableEvent exitEvent = (DSGExitTableEvent) dsgEvent;

            TableData data = getTableData(exitEvent.getTable());

            for (int i = 0; i < data.players.size(); i++) {
                DSGPlayerData d = (DSGPlayerData) data.players.elementAt(i);
                if (d.getName().equals(exitEvent.getPlayer())) {
                    data.players.removeElementAt(i);
                    break;
                }
            }

            if (exitEvent.getPlayer().equals(me.getName()) &&
                    data.clientTable != null) {

                // store the size of the table on server if changed
                Dimension tableSize = ((TableComponent) data.frame).getNewTableSizePref();
                if (tableSize != null) {
                    preferenceHandler.storePref("tableSize", tableSize);
                }
                dsgEventSource.removeListener(data.clientTable);
                data.clientTable.destroy();
                ((GameBoardFrame) data.frame).destroy();
                data.frame.dispose();
                data.frame = null;
            }
        } else if (dsgEvent instanceof DSGSitTableEvent) {
            DSGSitTableEvent sitEvent = (DSGSitTableEvent) dsgEvent;
            TableData data = getTableData(sitEvent.getTable());
            data.sittingPlayers[sitEvent.getSeat()] = sitEvent.getPlayer();
        } else if (dsgEvent instanceof DSGStandTableEvent) {
            DSGStandTableEvent standEvent = (DSGStandTableEvent) dsgEvent;
            TableData data = getTableData(standEvent.getTable());
            for (int i = 1; i < data.sittingPlayers.length; i++) {
                if (data.sittingPlayers[i] != null &&
                        data.sittingPlayers[i].equals(standEvent.getPlayer())) {
                    data.sittingPlayers[i] = null;
                }
            }
        } else if (dsgEvent instanceof DSGSwapSeatsTableEvent) {
            DSGSwapSeatsTableEvent swapEvent = (DSGSwapSeatsTableEvent) dsgEvent;
            if (swapEvent.wantsToSwap()) {
                TableData data = getTableData(swapEvent.getTable());
                String tmp = data.sittingPlayers[1];
                data.sittingPlayers[1] = data.sittingPlayers[2];
                data.sittingPlayers[2] = tmp;
            }
        }
    }

    public void destroy() {
        for (Enumeration e = tables.elements(); e.hasMoreElements(); ) {
            TableData d = (TableData) e.nextElement();
            if (d.clientTable != null) {
                dsgEventSource.removeListener(d.clientTable);
                d.clientTable.destroy();
            }
            if (d.frame != null) {
                ((GameBoardFrame) d.frame).destroy();
                d.frame.dispose();
                d.frame = null;
            }
        }
    }
}