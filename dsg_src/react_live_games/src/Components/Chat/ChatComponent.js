import React, {useEffect} from 'react';
import PropTypes from 'prop-types';
import Grid from '@material-ui/core/Grid';

import PlayersList from './PlayersList';
import ChatInput from './ChatInput';
import ChatPanel from './ChatPanel';

function ChatComponent(props) {

        useEffect(() => {
            let element = document.getElementById('chatdiv');
            element.scrollTop = element.scrollHeight;
        });
        
        const { users, sendRoomText, messages } = props;

        return (
            <Grid container direction={'row'} alignItems={'stretch'} wrap={'nowrap'} style={{width: '100%', height: '100%'}}>
                <Grid item style={{height: '100%'}}>
                     <div style={{overflow: 'auto', maxHeight:'100%'}}>
                         <PlayersList players={users} />
                     </div>
                </Grid>
                <Grid item style={{height:'100%', flex: '1', minWidth: '0px'}}>
                    <Grid container direction={'column'} alignItems={'stretch'}  wrap={'nowrap'}
                          style={{width: '100%', height: '100%'}}>
                        <Grid id={'chatdiv'} item 
                              style={{maxWidth: '100%', flex: '1 1 auto', overflow: 'auto', minHeight: '0px'}}>
                            <ChatPanel messages={messages}/>
                        </Grid>
                        <Grid item>
                            <ChatInput sendHandler={sendRoomText}/>
                        </Grid>
                    </Grid>
                </Grid>
            </Grid>
        );
}

export default ChatComponent;
