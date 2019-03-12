import React, { Component } from 'react';
import BoardSquare from './BoardSquare';


class Board extends Component {
    constructor(props) {
        super(props);
        let style;
        if (props.game < 3) {
            style = 'pente'
        } else if (props.game < 5) {
            style = 'keryo-pente'
        } else if (props.game < 7) {
            style = 'gomoku-pente'
        } else if (props.game < 9) {
            style = 'd-pente'
        } else if (props.game < 11) {
            style = 'g-pente'
        } else if (props.game < 13) {
            style = 'poof-pente'
        } else if (props.game < 15) {
            style = 'connect6'
        } else if (props.game < 17) {
            style = 'boat-pente'
        } else if (props.game < 19) {
            style = 'dk-pente'
        } else {
            style = 'go'
        } 
        this.state = {
            game: props.game,
            // gridsize: props.gridsize,
            size: props.size,
            boardstyle: 'board '+style
        }
    }
    
   
    makeBoard() {
        let gridsize = 19;
        if (this.state.game === 21 || this.state.game === 22) { gridsize = 9; }
        if (this.state.game === 23 || this.state.game === 24) { gridsize = 13; }
        const cellsize = this.state.size/gridsize;
        
        let board = [];
        for(let i = 0; i < gridsize*gridsize; i++) {
            let squaretype;
            if (i === 0) {
                squaretype = 1;
            } else if (i === gridsize - 1) {
                squaretype = 3;
            } else if (i === gridsize*gridsize - 1) {
                squaretype = 9;
            } else if (i === gridsize*(gridsize - 1)) {
                squaretype = 7;
            } else if (Math.floor(i / gridsize) === 0) {
                squaretype = 2;
            } else if (Math.floor(i / gridsize) === gridsize - 1) {
                squaretype = 8;
            } else if (i % gridsize === 0) {
                squaretype = 4;
            } else if (i % gridsize === gridsize - 1) {
                squaretype = 6;
            } else {
                squaretype = 5;
            }
            board.push(<BoardSquare key={i} size={cellsize} 
                                    gridsize={gridsize} id={i} 
                                    part={squaretype}
                                    canInteract={true}/>);
        }
        if (this.state.game < 19) {
            const circles = [120, 126, 180, 234, 240];
            circles.forEach(c => { board[c] = (<BoardSquare key={c} size={cellsize}
                                                            gridsize={gridsize} 
                                                            id={c} part={51}/>);})
        } else if (this.state.game === 19 || this.state.game === 20) {
            const dots = [60, 66, 72, 174, 180, 186, 288, 294, 300];
            dots.forEach(d => { board[d] = (<BoardSquare key={d} size={cellsize}
                                                            gridsize={gridsize}
                                                            id={d} part={52}/>);})
        } else if (this.state.game === 21 || this.state.game === 22) {
            const dots = [20, 24, 40, 56, 60];
            dots.forEach(d => { board[d] = (<BoardSquare key={d} size={cellsize}
                                                         gridsize={gridsize}
                                                         id={d} part={52}/>);})
        } else if (this.state.game === 23 || this.state.game === 24) {
            const dots = [42, 45, 48, 81, 84, 87, 120, 123, 126];
            dots.forEach(d => { board[d] = (<BoardSquare key={d} size={cellsize}
                                                         gridsize={gridsize}
                                                         id={d} part={52}/>);})
        }
        
        return board;
    }    
    
    render () {
        const t = this.state.size*2.5/100;
        return (
            <svg id="svgboard" height={this.state.size} width={this.state.size}>
                <g id="boardgroup" transform={'scale(0.95, 0.95) translate('+t+','+t+')'}>
                    <filter id="f3" x="0" y="0" width="150%" height="150%">
                        <feOffset result="offOut" in="SourceAlpha" dx={4} dy={4} />
                        <feGaussianBlur result="blurOut" in="offOut" stdDeviation="3" />
                        <feBlend in="SourceGraphic" in2="blurOut" mode="normal" />
                    </filter>
                    <rect height='100%' width='100%' filter={"url(#f3)"} className={this.state.boardstyle}/>
                    {this.makeBoard()}
                </g>
            </svg>
        )
    }
}

export default Board;