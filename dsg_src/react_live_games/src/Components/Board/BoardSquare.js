import React, { Component } from 'react';



class BoardSquare extends Component {
    constructor(props) {
        super(props);
        const gridsize = props.gridsize,
            y = props.size*Math.floor(parseInt(props.id)/gridsize),
            x = props.size*(parseInt(props.id)%gridsize);
        this.state = {
            showStone: false,
            x: x,
            y: y,
            canInteract: props.canInteract,
            mouseID: 'white-stone-gradient'
        }
    }
    
    up(size, transform) {
        return (
            <line key={this.props.id + transform} className='boardline' x1={size/2} y1={size/2}
                  x2={size/2} y2={0}
                  transform={transform}
            />
        )
    }
    left(size) { return this.up(size, 'rotate(-90 '+(size/2)+' '+(size/2)+')') }
    right(size) { return this.up(size, 'rotate(90 '+(size/2)+' '+(size/2)+')') }
    down(size) { return this.up(size, 'rotate(180 '+(size/2)+' '+(size/2)+')') }
    cross(size) { return ( [this.up(size), this.left(size), this.right(size), this.down(size)] ) }
    topside(size) { return ( [this.left(size), this.right(size), this.down(size)] ) }
    bottomside(size) { return ( [this.up(size), this.left(size), this.right(size)] ) }
    leftside(size) { return ( [this.up(size), this.right(size), this.down(size)] ) }
    rightside(size) { return ( [this.up(size), this.left(size), this.down(size)] ) }
    upperleftcorner(size) { return ( [this.right(size), this.down(size)] ) }
    upperrightcorner(size) { return ( [this.left(size), this.down(size)] ) }
    bottomleftcorner(size) { return ( [this.up(size), this.right(size)] ) }
    bottomrightcorner(size) { return ( [this.up(size), this.left(size)] ) }
    crosscircle(size) { return ( [<circle className='boardcircle' key={this.props.id + 'circle'} cx={size/2} cy={size/2} r={size/6} />, 
        this.up(size), this.left(size), this.right(size), this.down(size)] ) }
    crossdot(size) { return ( [this.up(size), this.left(size), this.right(size), this.down(size), 
        <circle className='boarddot' key={this.props.id + 'dot'} cx={size/2} cy={size/2} r={size/8} />] ) }
    boardpart(size) {
        switch(this.props.part) {
            case 1: return this.upperleftcorner(size);
            case 2: return this.topside(size);
            case 3: return this.upperrightcorner(size);
            case 4: return this.leftside(size);
            case 5: return this.cross(size);
            case 51: return this.crosscircle(size);
            case 52: return this.crossdot(size);
            case 6: return this.rightside(size);
            case 7: return this.bottomleftcorner(size);
            case 8: return this.bottomside(size);
            default: return this.bottomrightcorner(size);
        }
    }
    enterExitHandler(e) {
        if (! this.state.canInteract) { return; }
        this.setState( { ...this.state, showStone: !this.state.showStone })
    }
    clickHandler(e) {
        console.log(e.target.id)
        const newState = this.state;
        if (this.state.mouseID === 'white-stone-gradient') {
            newState.mouseID = 'black-stone-gradient';
        } else {
            newState.mouseID = 'white-stone-gradient';            
        }
        this.setState(newState);
    }

    mouseStone(size) {
        let id = this.state.mouseID;
        return (
            <svg key={1} height={size*1.5} width={size*1.5}>
                <radialGradient id={id} cx="33.33%" cy="33.33%" r="33.33%" fx="25%" fy="25%">
                    <stop offset="0%" stopColor='var(--color1)' />
                    <stop offset="100%" stopColor='var(--color2)' />
                </radialGradient>
                <filter id="f2" x="0" y="0" width="150%" height="150%">
                    <feOffset result="offOut" in="SourceAlpha" dx={4} dy={4} />
                    <feGaussianBlur result="blurOut" in="offOut" stdDeviation="3" />
                    <feBlend in="SourceGraphic" in2="blurOut" mode="normal" />
                </filter>
                <circle cx={size/2} cy={size/2} r={size/2} 
                        fill={"url(#"+id+")"} fillOpacity="0.60" 
                        pointerEvents={'none'} filter={"url(#f2)"}/>
            </svg>
        );
    }
    
    render () {
        return (
            <svg key={this.props.id} x={this.state.x} y={this.state.y} 
                 height={this.props.size*1.5} width={this.props.size*1.5}
                 onMouseEnter={this.enterExitHandler.bind(this)}
                 onMouseLeave={this.enterExitHandler.bind(this)}
                 onClick={this.clickHandler.bind(this)}
            >
                <rect id={this.props.id} width={this.props.size+1} height={this.props.size+1}
                       fillOpacity={0.0} />
                {this.boardpart(this.props.size)}
                {this.state.showStone?this.mouseStone(this.props.size):""}
            </svg>
        )
    }
}

export default BoardSquare;