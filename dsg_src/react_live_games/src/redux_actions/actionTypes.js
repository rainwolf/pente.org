import { WEBSOCKET_CONNECT, WEBSOCKET_SEND } from '@giantmachines/redux-websocket'

export const CONNECT_SERVER = 'CONNECT_SERVER';
export const SERVER_CONNECTED = 'SERVER_CONNECTED';

export function connectServer(server) {
    return {
        type: CONNECT_SERVER,
        payload: server
    }
}

export function connectSocket(server) {
    return {
        type: WEBSOCKET_CONNECT,
        payload: {
            url: 'wss://development.pente.org/websocketServer/'+server
        }
    }
}

export function send_message(payload) {
    // console.log('send '+ JSON.stringify(payload));
    return {
        type: WEBSOCKET_SEND,
        payload: payload
    }
}

