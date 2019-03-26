import React, { Component } from 'react';
import Stone from './Stone';


class BoardSquare extends Component {
    constructor(props) {
        super(props);
        this.state = {
            showStone: false,
            keepshowing: false
            // mouseID: this.props.hover
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
    crosscircle(size) { return ( [<circle className='boardcircle' key={this.props.id + 'circle'} 
                                          cx={size/2} cy={size/2} r={size/5} fill='none'/>, 
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
        if (this.props.clickHandler === undefined) { return; }
        this.setState( { ...this.state, showStone: !this.state.showStone })
    }
    clickHandler(e) {
        this.setState({keepshowing: true})
        if (this.props.clickHandler === undefined) { return; }
        this.props.clickHandler(e.target.id);
        // const newState = this.state;
        // if (this.state.mouseID === 'white-stone-gradient') {
        //     newState.mouseID = 'black-stone-gradient';
        // } else {
        //     newState.mouseID = 'white-stone-gradient';            
        // }
        // this.setState(newState);
    }

    
    render () {
        const gridsize = this.props.gridsize,
            // size = Math.floor(100/gridsize),
            size = 10,
            y = 10*Math.floor(parseInt(this.props.id)/gridsize),
            x = 10*(parseInt(this.props.id)%gridsize);
        return (
            <svg key={this.props.id} viewBox={'0 0 15 15'} 
                 height={size*1.5} width={size*1.5}
                 onMouseEnter={this.enterExitHandler.bind(this)}
                 onMouseLeave={this.enterExitHandler.bind(this)}
                 onClick={this.clickHandler.bind(this)}
                 transform={'translate('+x+','+y+')'}
            >
                <rect id={this.props.id} width={size+1} height={size+1}
                       fillOpacity={0.0} />
                {this.boardpart(size)}
                {this.state.showStone || this.state.keepshowing?Stone({size: 10, id: this.props.hover, opacity: 0.6}):""}
            </svg>
        )
    }
}

export default BoardSquare;