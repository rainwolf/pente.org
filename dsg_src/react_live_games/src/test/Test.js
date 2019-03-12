import React, { Component } from 'react';
import Board from '../Components/Board/Board';

class Test extends Component {
    render () {
        return (
            <div>
                <Board game={3} size={500}/>
                <Board game={19} size={500}/>
                <Board game={21} size={500}/>
                <Board game={23} size={500}/>
            </div>
        )
    }
}

export default Test;