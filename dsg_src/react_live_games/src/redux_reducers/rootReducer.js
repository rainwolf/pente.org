import '../redux_actions/actionTypes';
import { CONNECT_SERVER } from "../redux_actions/actionTypes";
import { WEBSOCKET_OPEN, WEBSOCKET_CLOSED, WEBSOCKET_MESSAGE } from '@giantmachines/redux-websocket';
import './utils';
import {processUser, addRoomMessage, exitUser} from "./utils";


const initialState = {
    users: {},
    tables: [],
    room_messages: [],
    connected: false,
    logged_in: false,
    table_id: undefined
};

function liveGameApp (state = initialState, action) {
    let newState = { ...state };
    switch(action.type) {
        case CONNECT_SERVER:
            newState.server = action.payload;
            break;
        case WEBSOCKET_OPEN:
            console.log('socket open');
            newState.room = {};
            newState.connected = true;
            break;
        case WEBSOCKET_CLOSED:
            console.log('socket closed');
            return initialState;
            // newState.room = {};
            // newState.connected = false;
            // break;
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
                processUser(json.dsgJoinMainRoomEvent, newState);
            } else if (json.dsgTextMainRoomEvent) {
                addRoomMessage(json.dsgTextMainRoomEvent, newState);
            } else if (json.dsgExitMainRoomEvent) {
                exitUser(json.player, newState);
            }
            // console.log(json);
            break;
    }
    return newState;
}

export default liveGameApp;