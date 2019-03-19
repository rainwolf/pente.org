import React from 'react';
import ReactDOM from 'react-dom';
import './index.css';
import App from './App';
import { createStore, applyMiddleware } from 'redux';
import { Provider } from 'react-redux';
import * as serviceWorker from './serviceWorker';
import liveGameApp from './redux_reducers/rootReducer';
import websocket, { WEBSOCKET_MESSAGE, WEBSOCKET_SEND } from '@giantmachines/redux-websocket';

// ping middleware to respond to pings
const pinger = store => next => action => {
    let result = next(action);
    if (action.type === WEBSOCKET_MESSAGE && action.payload.data.indexOf('dsgPingEvent') > -1) {
        const replyPing = { type: WEBSOCKET_SEND, payload: JSON.parse(action.payload.data) };
        replyPing.type = WEBSOCKET_SEND;
        store.dispatch(replyPing);
    }
    return result
};

const store = createStore(liveGameApp, applyMiddleware(websocket, pinger));

ReactDOM.render(
    <Provider store={store}>
        <App />
    </Provider>,
    document.getElementById('root')
);

// If you want your app to work offline and load faster, you can change
// unregister() to register() below. Note this comes with some pitfalls.
// Learn more about service workers: http://bit.ly/CRA-PWA
serviceWorker.unregister();
