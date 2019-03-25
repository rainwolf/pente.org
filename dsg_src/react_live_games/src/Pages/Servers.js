import React, { Component } from 'react';
// import { WEBSOCKET_CONNECT } from '@giantmachines/redux-websocket';
import { connect } from 'react-redux';
import { connectServer, connectSocket } from '../redux_actions/actionTypes';

const mapDispatchToProps = dispatch => {
    return {
        onServerClick: server => {
            dispatch(connectServer(server));
            dispatch(connectSocket(server.port));
        }
    }
};

class UnconnectedServers extends Component {
    constructor(props) {
        super(props);
        this.state = {
            servers: []
        }
    }
    
    componentDidMount() {
        this.reloadServers();
    }
    reloadServers() {
        fetch('/gameServer/activeServers')
        .then(response => response.text())
        .then(res => {
            const splt = res.split('\n');
            let servers = [];
            for(let i = 0; i<splt.length; i++) {
                const str = splt[i];
                if (str.length === 0) { continue; }
                const port = str.substr(0, str.indexOf(' '));
                const name = str.substr(str.indexOf(' ') + 1);
                servers.push({port: port, name: name});
            }
            this.setState( {servers: servers} );
        });
    }
    
    render () {
        return (
            <div>
                {this.state.servers.map((server, i) => 
                    (
                        <div key={i} style={{height: 100, width: "100%", border: "solid", verticalAlign: "middle"}} align="center"
                          onClick={() => this.props.onServerClick(server)}>
                            <h1>{server.name}</h1>
                        </div>
                    )
                )}
            </div>
        )
    }
}

const Servers = connect(undefined, mapDispatchToProps)(UnconnectedServers);

export default Servers;