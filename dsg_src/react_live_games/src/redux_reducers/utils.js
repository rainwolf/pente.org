import Table from './TableClass';
import User from './UserClass';


export function processUser(userdata, state) {
    let user;
    if (state.users[userdata.name]) {
        user = state.users[userdata.name];
    } else {
        user = new User(userdata);
    }
    state.users = { ...state.users, [user.name]: user };
}
       
export function addRoomMessage(data, state) {
    const messages = state.room_messages.slice();
    const user = state.users[data.player];
    messages.push( {
        message: data.text,
        player: user
    });
    state.room_messages = messages;
}

export function exitUser(name, state) {
    const {[name]: value, ...rest} = state.users;
    state.users = rest;
}

export function changeTableState(tableState, state) {
    let tables = { ...state.tables };
    if (tables[tableState.table]) {
        tables[tableState.table].updateTable(tableState);
    } else {
        tables[tableState.table] = new Table(tableState);
    }
    state.tables = tables;
}

export function joinTable(joinEvent, state) {
    const tables = { ...state.tables };
    let table = tables[joinEvent.table];
    if (table === undefined) {  
        table = new Table({table: joinEvent.table})
        tables[joinEvent.table] = table;
    }
    table.addPlayer(joinEvent.player);
    state.tables = tables;
}

export function exitTable(exitEvent, state) {
    const tables = { ...state.tables };
    const table = tables[exitEvent.table];
    if (table) {
        table.removePlayer(exitEvent.player);
        if (table.players.length === 0) { delete tables[exitEvent.table]} 
    }
    state.tables = tables;
}

export function sitTable(sitEvent, state) {
    const tables = { ...state.tables };
    const table = tables[sitEvent.table];
    table.sit(sitEvent.player, sitEvent.seat);
    state.tables = tables;
}

export function standTable(standEvent, state) {
    const tables = { ...state.tables };
    const table = tables[standEvent.table];
    table.stand(standEvent.player);
    state.tables = tables;
}

