import Table from './TableClass';
import User from './UserClass';
import Game from './GameClass';

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
    if (state.table === tableState.table) {
        let game = new Game();
        game.me = state.me;
        game.setGame(tableState.game);
        game.rated = tableState.rated;
        state.game = game;
    } 
    state.tables = tables;
}

export function joinTable(joinEvent, state) {
    const tables = { ...state.tables };
    let table = tables[joinEvent.table];
    if (table === undefined) {  
        table = new Table({table: joinEvent.table});
        tables[joinEvent.table] = table;
    }
    if (joinEvent.player === state.me) {
        state.table = joinEvent.table;
        table.me = state.me;
        if (state.game === undefined) {
            state.game = new Game();
        } 
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
    if (exitEvent.player === state.me) {
        state.table = undefined;
        state.game = undefined;
        state.table_messages = [];
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

export function tableOwner(ownerEvent, state) {
    const tables = { ...state.tables };
    const table = tables[ownerEvent.table];
    table.owner = ownerEvent.player === state.me;
    state.tables = tables;
}

export function addTableMessage(data, state) {
    const messages = [...state.table_messages];
    const user = state.users[data.player];
    messages.push( {
        message: data.text,
        player: user
    });
    state.table_messages = messages;
}

export function addMove(data, state) {
    // let game = new Game();
    // state.game.copyOnto(game);
    let game =  Object.assign( Object.create( Object.getPrototypeOf(state.game)), state.game);
    if (data.table === state.table) {
        if (data.moves.length === 1 && data.move === data.moves[0]) {
            game.addMove(data.move);
        } else {
            game.reset();
            
            for(let i = 0; i < data.moves.length; i++) {
                game.addMove(data.moves[i]);
            }
        } 
    }
    state.game = game;
}