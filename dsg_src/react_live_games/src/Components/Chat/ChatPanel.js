import React, { useEffect } from 'react';
import PropTypes from 'prop-types';
import { withStyles } from '@material-ui/core/styles';
import List from '@material-ui/core/List';
import ListItem from '@material-ui/core/ListItem';
import ListItemText from '@material-ui/core/ListItemText';
import ListItemAvatar from '@material-ui/core/ListItemAvatar';
import Avatar from '@material-ui/core/Avatar';
// import Typography from '@material-ui/core/Typography';

const styles = theme => ({
    root: {
        width: '100%',
        maxWidth: '100%',
        backgroundColor: theme.palette.background.paper,
    }
});

function ChatPanel(props) {
    
    // useEffect(() => {
    //     let element = document.getElementById('chatdiv0');
    //     element.scrollTop = element.scrollHeight;
    // });
    
    const { classes, messages } = props;
    return (
            <List className={classes.root}>
                {messages.map((message, i) => (
                    <ListItem key={i} alignItems="flex-start">
                        <ListItemAvatar>
                            <Avatar alt="avatar" src={message.player.avatar} />
                        </ListItemAvatar>
                        <ListItemText
                            primary={message.player.userhtml}
                            secondary={' - ' + message.message}
                            style={{wordWrap: 'break-word'}}
                        />
                    </ListItem>

                ))}
            </List>
    );
}

ChatPanel.propTypes = {
    classes: PropTypes.object.isRequired,
    messages: PropTypes.arrayOf(
        PropTypes.shape({
            message: PropTypes.string.isRequired,
            // player: 
        }).isRequired
    ).isRequired
};

export default withStyles(styles)(ChatPanel);
