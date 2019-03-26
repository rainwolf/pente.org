import React, { Component } from 'react';
import Board from '../Components/Board/Board';

class Test extends Component {
    constructor(props) {
        super(props);
        this.state = {
            game: 21
        }
    }
    
    changeGame(e) {
        this.setState({game: parseInt(e.target.value)})
        // console.log(this.state)
    }
    
    clickHandler(i) {
        console.log('clicked '+i)
    }
    
    render () {
        const size = 600;
        // console.log('render')
        return (
            <div style={{width: '700px', height: '500px'}}>
                <select onChange={this.changeGame.bind(this)}>
                    <option value={1}>Pente</option>
                    <option value={3}>Keryo-Pente</option>
                    <option value={19}>Go</option>
                    <option value={21}>Go (9x9)</option>
                </select>
                <Board game={this.state.game} 
                       clickHandler={this.clickHandler}
                       hover={'black-stone-gradient'}
                       
                />
            </div>
        )
    }
}

export default Test;