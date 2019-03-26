import React, { useState, useEffect, useRef } from 'react';
import { connect } from 'react-redux';
import { send_message } from "../redux_actions/actionTypes";
import PropTypes from 'prop-types';
import User from "../redux_reducers/UserClass";
import Game from '../redux_reducers/GameClass';
import Table from '../redux_reducers/TableClass';
import Board from '../Components/Board/Board';
import Grid from '@material-ui/core/Grid';
import ChatComponent from '../Components/Chat/ChatComponent';

const mapStateToProps = state => {
    return {
        users: state.users,
        game: state.game,
        messages: state.table_messages,
        table: state.tables[state.table]
    }
};

const mapDispatchToProps = dispatch => {
    return {
        send_message: message => {
            dispatch(send_message(message));
        }
    }
};


const UnconnectedTable = (props) => {

    const [height, setHeight] = useState(0);
    const ref = useRef(null);

    useEffect(() => {
        setHeight(ref.current.clientHeight)
    });

    const sendTableText = (event) => {
        const str = event.target.value;
        if(event.key === 'Enter' && str !== '') {
            props.send_message({dsgTextTableEvent: {text: str, table: props.table.table, time: 0}});
            event.target.value = "";
        }
    };
    
    let table_users = {};
    props.table.players.forEach(player => {
       table_users[player] = props.users[player]; 
    });
    
    return (
        <Grid container direction={'row'} alignItems={'stretch'} wrap={'nowrap'} style={{width: '100%', height: '100%'}}>
            <Grid item style={{height: '100%'}}>
                
                <div ref={ref} style={{height: '100%', width: height}}>
                    <Board game={props.table.game}
                           clickHandler={() => {}} hover={'black-stone-gradient'}/>
                </div>
            </Grid>
            <Grid item style={{height:'100%', flex: '1', minWidth: '0px'}}>
                <Grid container direction={'column'} alignItems={'stretch'}  wrap={'nowrap'}
                      style={{width: '100%', height: '100%'}}>
                    <Grid item style={{maxWidth: '100%', flex: '1 1 auto', overflow: 'auto', minHeight: '0px'}}>
                        <div style={{width: '100%', height: '100%', backgroundColor: '#ff00ff'}}>herro</div>
                    </Grid>
                    <Grid item style={{height: '40%'}}>
                        <ChatComponent messages={props.messages} users={table_users} sendText={sendTableText}/>
                    </Grid>
                </Grid>
            </Grid>
        </Grid>
    );
};

UnconnectedTable.propTypes = {
    users: PropTypes.objectOf(
        PropTypes.instanceOf(User).isRequired
    ).isRequired,
    messages: PropTypes.arrayOf(
        PropTypes.shape({
            message: PropTypes.string.isRequired,
            player: PropTypes.instanceOf(User).isRequired
        }).isRequired
    ).isRequired,
    table: PropTypes.instanceOf(Table).isRequired,
    // game: PropTypes.instanceOf(Game).isRequired
    game: PropTypes.instanceOf(Game)
};


const TableView = connect(mapStateToProps, mapDispatchToProps)(UnconnectedTable);

export default TableView;