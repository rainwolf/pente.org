import React, { useState } from 'react';
import Stone from './Stone';


const BoardSquare = (props) => {
    
    const [showStone, toggleShow] = useState(false);
    
    const up = (size, transform) => {
        return (
            <line key={props.id + transform} className='boardline' x1={size/2} y1={size/2}
                  x2={size/2} y2={0}
                  transform={transform}
            />
        )
    };
    const left = (size) => { return up(size, 'rotate(-90 '+(size/2)+' '+(size/2)+')') };
    const right = (size) => { return up(size, 'rotate(90 '+(size/2)+' '+(size/2)+')') };
    const down = (size) => { return up(size, 'rotate(180 '+(size/2)+' '+(size/2)+')') };
    const cross = (size) => { return ( [up(size), left(size), right(size), down(size)] ) };
    const topside = (size) => { return ( [left(size), right(size), down(size)] ) };
    const bottomside = (size) => { return ( [up(size), left(size), right(size)] ) };
    const leftside = (size) => { return ( [up(size), right(size), down(size)] ) };
    const rightside = (size) => { return ( [up(size), left(size), down(size)] ) };
    const upperleftcorner = (size) => { return ( [right(size), down(size)] ) };
    const upperrightcorner = (size) => { return ( [left(size), down(size)] ) };
    const bottomleftcorner = (size) => { return ( [up(size), right(size)] ) };
    const bottomrightcorner = (size) => { return ( [up(size), left(size)] ) };
    const crosscircle = (size) => { return ( [<circle className='boardcircle' key={props.id + 'circle'} 
                                      cx={size/2} cy={size/2} r={size/5} fill='none'/>, 
    up(size), left(size), right(size), down(size)] ) };
    const crossdot = (size) => { return ( [up(size), left(size), right(size), down(size), 
         <circle className='boarddot' key={props.id + 'dot'} cx={size/2} cy={size/2} r={size/8} />] ) };
    const boardpart = (size) => {
         switch(props.part) {
            case 1: return upperleftcorner(size);
            case 2: return topside(size);
            case 3: return upperrightcorner(size);
            case 4: return leftside(size);
            case 5: return cross(size);
            case 51: return crosscircle(size);
            case 52: return crossdot(size);
            case 6: return rightside(size);
            case 7: return bottomleftcorner(size);
            case 8: return bottomside(size);
            default: return bottomrightcorner(size);
        }
    };
    const enterExitHandler = (e) => {
        if (props.clickHandler === undefined) { return; }
        toggleShow(!showStone);
    };
    const clickHandler = (e) => {
        if (props.clickHandler === undefined) { return; }
        props.clickHandler(e.target.id);
        toggleShow(false);
    };

    
    const gridsize = props.gridsize,
        size = 10,
        y = 10*Math.floor(parseInt(props.id)/gridsize),
        x = 10*(parseInt(props.id)%gridsize);
    return (
        <g key={props.id} viewBox={'0 0 15 15'} 
             height={size*1.5} width={size*1.5}
             onMouseEnter={enterExitHandler}
             onMouseLeave={enterExitHandler}
             onClick={clickHandler}
             transform={'translate('+x+','+y+')'}
        >
            <rect id={props.id} width={size+1} height={size+1}
                   fillOpacity={0.0} />
            {boardpart(size)}
            {props.stone && Stone({size: 10, id: props.stone})}
            {showStone && Stone({size: 10, id: props.hover, opacity: 0.6})}
        </g>
    );
};

export default BoardSquare;