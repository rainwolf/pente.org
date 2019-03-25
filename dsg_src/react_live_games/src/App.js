import React from 'react';
import './App.css';
import Servers from './Pages/Servers';
import Room from "./Pages/Room";
import { connect } from 'react-redux';
import Table from "./Pages/Table";

const mapStateToProps = state => {
    return {
        table: state.table,
        room: state.server
    }
};

const UnconnectedApp = ({table, room}) => {
    if (table) {
        return <Table/>
    } else if (room) {
        return <Room/>
    } else {
        return <Servers/>
    } 
};

const App = connect(mapStateToProps)(UnconnectedApp);

export default App;
