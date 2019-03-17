import React from 'react';
import './App.css';
import Servers from './Pages/Servers';
import Room from "./Pages/Room";
import { connect } from 'react-redux';
import Table from "./Pages/Table";

const mapStateToProps = state => {
    return {
        table_id: state.current_table_id,
        room: state.server
    }
};

const UnconnectedApp = ({table_id, room}) => {
    if (table_id) {
        return <Table/>
    } else if (room) {
        return <Room/>
    } else {
        return <Servers/>
    } 
};

const App = connect(mapStateToProps)(UnconnectedApp);

export default App;
