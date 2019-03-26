import '../redux_actions/actionTypes';
import { CONNECT_SERVER } from "../redux_actions/actionTypes";
import { WEBSOCKET_OPEN, WEBSOCKET_CLOSED, WEBSOCKET_MESSAGE } from '@giantmachines/redux-websocket';
import './utils';
import {processUser, addRoomMessage, exitUser, changeTableState,
    joinTable, exitTable, sitTable, standTable, tableOwner,
    addTableMessage, addMove} from "./utils";


const initialState = {
    users: {},
    tables: {},
    room_messages: [],
    connected: false,
    logged_in: false,
    table: undefined,
    game: undefined,
    table_messages: []
};

function liveGameApp (state = initialState, action) {
    let newState = { ...state };
    switch(action.type) {
        case CONNECT_SERVER:
            newState.server = action.payload;
            break;
        case WEBSOCKET_OPEN:
            console.log('socket open');
            newState.connected = true;
            break;
        case WEBSOCKET_CLOSED:
            console.log('socket closed');
            return initialState;
        case WEBSOCKET_MESSAGE:
            console.log(action.payload.data);
            const json = JSON.parse(action.payload.data);
            if (json.dsgLoginErrorEvent) {
                return initialState;
            } else if (json.dsgLoginEvent) {
                newState.me = json.dsgLoginEvent.player;
                newState.logged_in = true;
            } else if (json.dsgPingEvent) {
                console.log('ping: ' + action.payload.data)
            } else if (json.dsgJoinMainRoomEvent) {
                processUser(json.dsgJoinMainRoomEvent.dsgPlayerData, newState);
            } else if (json.dsgUpdatePlayerDataEvent) {
                processUser(json.dsgUpdatePlayerDataEvent.data, newState);
            } else if (json.dsgTextMainRoomEvent) {
                addRoomMessage(json.dsgTextMainRoomEvent, newState);
            } else if (json.dsgExitMainRoomEvent) {
                exitUser(json.dsgExitMainRoomEvent.player, newState);
            } else if (json.dsgChangeStateTableEvent) {
                changeTableState(json.dsgChangeStateTableEvent, newState);
            } else if (json.dsgJoinTableEvent) {
                joinTable(json.dsgJoinTableEvent, newState);
            } else if (json.dsgExitTableEvent) {
                exitTable(json.dsgExitTableEvent, newState);
            } else if (json.dsgSitTableEvent) {
                sitTable(json.dsgSitTableEvent, newState);
            } else if (json.dsgStandTableEvent) {
                standTable(json.dsgStandTableEvent, newState);
            } else if (json.dsgOwnerTableEvent) {
                tableOwner(json.dsgOwnerTableEvent, newState);
            } else if (json.dsgTextTableEvent) {
                addTableMessage(json.dsgTextTableEvent, newState);
            } else if (json.dsgMoveTableEvent) {
                addMove(json.dsgMoveTableEvent, newState);
            }
        // {"dsgSetPlayingPlayerTableEvent":{"seat":2,"player":"iostest","table":1,"time":1553629064097}} rootReducer.js:35
        // {"dsgGameStateTableEvent":{"state":2,"changeText":"game 1 of set started","gameInSet":1,"table":1,"time":1553629064101}} rootReducer.js:35
        // {"dsgMoveTableEvent":{"move":180,"moves":[180],"player":"rainwolf","table":1,"time":1553629064102}}
            // console.log(json);
            break;
        default: break;
    }
    return newState;
}

export default liveGameApp;