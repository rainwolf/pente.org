import React from 'react';
import './App.css';
import Servers from './Pages/Servers';
import Room from "./Pages/Room";
import { connect } from 'react-redux';
import TableView from "./Pages/Table";
import Test from "./test/Test";

const mapStateToProps = state => {
    return {
        table: state.table,
        room: state.server
    }
};

const UnconnectedApp = ({table, room}) => {
    // return <Test/>;
    if (table) {
        return (
            <div style={{width: '100vw', height: '100vh'}}>
                <TableView/>
            </div>
        );
    } else if (room) {
        return <Room/>
    } else {
        return <Servers/>
    } 
};

const App = connect(mapStateToProps)(UnconnectedApp);

export default App;
