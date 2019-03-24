class Table {
    constructor(tableState) {
        Object.assign(this, tableState);
        this.seats = [undefined, "", ""];
        this.players = [];
        this.clocks = [ { minutes: tableState.initialMinutes, seconds: 0 },
            { minutes: tableState.initialMinutes, seconds: 0 } ];
    }
    
    table_color = () => {
        let style;
        if (this.game < 3) {
            style = '#FDDEA3'
        } else if (this.game < 5) {
            style = '#BAFDA3'
        } else if (this.game < 7) {
            style = '#A3FDEB'
        } else if (this.game < 9) {
            style = '#A3CDFD'
        } else if (this.game < 11) {
            style = '#AEA3FD'
        } else if (this.game < 13) {
            style = '#EDA3FD'
        } else if (this.game < 15) {
            style = '#EDA3FD'
        } else if (this.game < 17) {
            style = '#25BAFF'
        } else if (this.game < 19) {
            style = '#FFA500'
        } else {
            style = '#FAC832'
        }
        return style;
    };
    
    game_name = () => {
        let name;
        if (this.game < 3) {
            name = 'Pente';
        } else if (this.game < 5) {
            name = 'Keryo-Pente';
        } else if (this.game < 7) {
            name = 'Gomoku';
        } else if (this.game < 9) {
            name = 'D-Pente';
        } else if (this.game < 11) {
            name = 'G-Pente';
        } else if (this.game < 13) {
            name = 'Poof-Pente';
        } else if (this.game < 15) {
            name = 'Connect6';
        } else if (this.game < 17) {
            name = 'Boat-Pente';
        } else if (this.game < 19) {
            name = 'DK-Pente';
        } else if (this.game < 21) {
            name = 'Go';
        } else if (this.game < 23) {
            name = 'Go (9x9)';
        } else if (this.game < 25) {
            name = 'Go (13x13)';
        }
        if (this.game % 2 === 0) {
            return 'Speed '+name;
        }
        return name;
    };
    
    updateTable = (tableState) => {
        Object.assign(this, tableState);
    };
    
    addPlayer = (player) => {
        this.players.push(player); 
    };
    
    removePlayer = (player) => {
        this.players = this.players.splice(this.players.indexOf(player), 1);
    };  
    
    watching = () => {
        return this.players.filter(player => this.seats[1] !== player && this.seats[2] !== player);
    };

    sit = (player, seat) => {
        this.seats[seat] = player;
    };
    
    stand = (player) => {
        for(let i = 1; i < 3; i++ ) {
            if (this.seats[i] === player) {
                this.seats[i] = "";
                break;
            } 
        }
    };
}

export default Table;