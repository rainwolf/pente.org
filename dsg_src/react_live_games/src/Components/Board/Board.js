import React  from 'react';
import BoardSquare from './BoardSquare';


const Board = (props) => {
    
    const game = props.gameObj;
    const table = props.table;
    
    const makeBoard = (gridsize) => {
        if (game === undefined || table === undefined) { return [] };
        let board = [];
        let player_colors = [undefined, 'white-stone-gradient', 'black-stone-gradient'];
        if (game.isGo()) { player_colors = [undefined, 'black-stone-gradient', 'white-stone-gradient']; }
        let hover = player_colors[game.currentColor()];
        const myTurn = table.isMyTurn(game);
        // console.log('my turn: ', myTurn)
        for(let j = 0; j < gridsize; j++) {
            for (let i = 0; i < gridsize; i++) {
                const m = j*gridsize + i;
                let squaretype;
                if (m === 0) {
                    squaretype = 1;
                } else if (m === gridsize - 1) {
                    squaretype = 3;
                } else if (m === gridsize * gridsize - 1) {
                    squaretype = 9;
                } else if (m === gridsize * (gridsize - 1)) {
                    squaretype = 7;
                } else if (Math.floor(m / gridsize) === 0) {
                    squaretype = 2;
                } else if (Math.floor(m / gridsize) === gridsize - 1) {
                    squaretype = 8;
                } else if (m % gridsize === 0) {
                    squaretype = 4;
                } else if (m % gridsize === gridsize - 1) {
                    squaretype = 6;
                } else {
                    squaretype = 5;
                }

                const stone = player_colors[game.abstractBoard[i][j]];
                let clickHandler = undefined;
                if (myTurn) {
                    if (game.abstractBoard[i][j] === 0) {
                        clickHandler = props.clickHandler;
                    } 
                    if (game.isGo() && game.doublePass) {
                        if (clickHandler === undefined) {
                            clickHandler = props.clickHandler;
                        } else {
                            clickHandler = undefined;
                        } 
                    }
                }
                board.push({ key: m, gridsize: gridsize, id: m,
                            part: squaretype, stone: stone,
                            clickHandler: clickHandler,
                            hover: hover});
            }
        }
        if (props.game < 19) {
            const circles = [120, 126, 180, 234, 240];
            circles.forEach(c => { board[c].part = 51; });
        } else {
            let dots;
            if (props.game === 19 || props.game === 20) {
                dots = [60, 66, 72, 174, 180, 186, 288, 294, 300];
            } else if (props.game === 21 || props.game === 22) {
                dots = [20, 24, 40, 56, 60];
            } else if (props.game === 23 || props.game === 24) {
                dots = [42, 45, 48, 81, 84, 87, 120, 123, 126];
            }
            dots.forEach(d => { board[d].part = 52; });
        } 
        
        return board.map(p => BoardSquare(p));
    };
    const makeCoordinateBoundaries = (gridsize) => {
        const coordinateLetters =['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T'];
        let coordinates = [];
        for ( let i = 1; i <= gridsize; i++ ) {
            coordinates.push(
                <text key={i} y={1} fontSize={4} transform={'translate(0,'+(10*i)+')'}>
                    {gridsize - i + 1}
                </text>
            );
            coordinates.push(
                <text key={coordinateLetters[i]+i} y={4} fontSize={4} transform={'translate('+(10*i)+',0)'}>
                    {coordinateLetters[i-1]}
                </text>
            )
        }
        return coordinates;           
    };
    
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
    let gridsize = 19;
    if (props.game === 21 || props.game === 22) { gridsize = 9; }
    if (props.game === 23 || props.game === 24) { gridsize = 13; }
    return (
        <svg id="svgboard" height={'100%'} viewBox={'0 0 '+(10*(gridsize + 1))+' '+(10*(gridsize + 1))}
             transform={'translate(25,25)'}>
            <g id="boardgroup" transform={'scale(0.95, 0.95) translate(5,5)'}>
                <filter id="f3" x="0" y="0" width="150%" height="150%">
                    <feOffset result="offOut" in="SourceAlpha" dx={4} dy={4} />
                    <feGaussianBlur result="blurOut" in="offOut" stdDeviation="3" />
                    <feBlend in="SourceGraphic" in2="blurOut" mode="normal" />
                </filter>
                <rect height={10*gridsize} width={10*gridsize} filter={"url(#f3)"} className={'board '+style}/>
                {makeBoard(gridsize)}
            </g>
            <g id="coordinates" transform={'scale(0.95, 0.95)'}>
            {makeCoordinateBoundaries(gridsize)}
            </g>
        </svg>
    );
};

export default Board;