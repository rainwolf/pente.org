import React, { Component } from 'react';
import { connect } from 'react-redux';
import { send_message } from "../redux_actions/actionTypes";

const mapStateToProps = state => {
    return {
        users: state.users,
        connected: state.connected,
        logged_in: state.logged_in
    }
};

const mapDispatchToProps = dispatch => {
    return {
        login: message => {
            dispatch(send_message(message));
        }
    }
};


class UnconnectedRoom extends Component {

    componentDidUpdate() {
        const {connected, logged_in, login} = this.props;
        // console.log("meep connected: " + connected + " logged in: "+ logged_in);
        if (connected && !logged_in) {
            const json = JSON.parse('{"dsgLoginEvent":{"player":"rainwolf","password":"","guest":false,"time":0}}');
            login(json);
        }
    }


    render () {
        const {users, connected, logged_in} = this.props;
        if (logged_in) {
            return (
                <div align="center">
                    <h1>Logged in... bloop</h1>
                    <div>
                        {Object.keys(users).map(user => users[user].name)}
                    </div>
                </div>
            )
        } else if (connected) {
            return (
                <div align="center">
                    <h1>Connected... bleep</h1>
                </div>
            )
        } else {
            return (
                <div align="center">
                    <h1>Connecting...</h1>
                </div>
            )
        }
    }
}

const Room = connect(mapStateToProps, mapDispatchToProps)(UnconnectedRoom);

export default Room;