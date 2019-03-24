import React from 'react';
import PropTypes from 'prop-types';
import { withStyles } from '@material-ui/core/styles';
import List from '@material-ui/core/List';
import ListItem from '@material-ui/core/ListItem';
import ListItemText from '@material-ui/core/ListItemText';
import ListItemAvatar from '@material-ui/core/ListItemAvatar';
import Avatar from '@material-ui/core/Avatar';
// import Typography from '@material-ui/core/Typography';
import User from '../../redux_reducers/UserClass';

const styles = theme => ({
    root: {
        width: '100%',
        maxWidth: 360,
        backgroundColor: theme.palette.background.paper,
    }
});

function PlayersList(props) {
    const { classes, players, game } = props;
    
    // const listhtml = Object.keys(players).map((name, i) => {
    //     const player = players[name];
    //     return (
    //         <ListItem key={i} >
    //             <ListItemAvatar>
    //                 <Avatar alt="Remy Sharp" src={player.avatar} />
    //             </ListItemAvatar>
    //             <ListItemText
    //                 primary={player.userhtml}
    //                 secondary={player.rating(game)}
    //             />
    //         </ListItem>
    //
    //     )
    // });
    // for (let i=0; i<15; i++) {
    //     listhtml.push(listhtml[0]);
    // }
    
    return (
        <List className={classes.root}>
            {Object.keys(players).map((name, i) => {
                    const player = players[name];
                    return (
                        <ListItem key={i} >
                            <ListItemAvatar>
                                <Avatar alt="Remy Sharp" src={player.avatar} />
                            </ListItemAvatar>
                            <ListItemText
                                primary={player.userhtml}
                                secondary={player.rating(game)}
                            />
                        </ListItem>
    
                    )
                })
            }
        </List>
    );
}

PlayersList.propTypes = {
    classes: PropTypes.object.isRequired,
    players: PropTypes.objectOf(
        PropTypes.instanceOf(User).isRequired
    ).isRequired
};

export default withStyles(styles)(PlayersList);
