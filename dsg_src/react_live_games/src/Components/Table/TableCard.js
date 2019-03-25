import React from 'react';
import PropTypes from 'prop-types';
import Table from '../../redux_reducers/TableClass';
import User from '../../redux_reducers/UserClass';
import Typography from '@material-ui/core/Typography';
import Card from '@material-ui/core/Card';
import CardContent from '@material-ui/core/CardContent';
import SimpleStone from '../Board/SimpleStone';
import HttpsIcon from '@material-ui/icons/Https';
import { withStyles } from '@material-ui/core/styles';


const styles = theme => ({
    card: {
        minWidth: 275,
        backgroundColor: '#FDDEA3'
    },
    bullet: {
        display: 'inline-block',
        margin: '0 2px',
        transform: 'scale(0.8)',
    },
    title: {
        fontSize: 14,
    },
    pos: {
        marginBottom: 12,
    },
    icon: {
        fontSize: 26,
    }
});

const TableCard = (props) => {
    const { classes, table, users } = props;
    const seated = table.seats[1] !== "" || table.seats[2] !== "";
    
    return (
        <Card className={classes.card} style={{backgroundColor: table.table_color()}} onClick={() => props.joinRoom(table.table)}>
            <CardContent>
                {/*<Typography className={classes.title} color="textSecondary" gutterBottom>*/}
                    {/*Word of the Day*/}
                {/*</Typography>*/}
                <Typography variant="h5" component="h2">
                    {table.private()?(<span><HttpsIcon className={classes.icon} /> {table.game_name()}</span>):table.game_name()}
                </Typography>
                {/*<Typography className={classes.pos} color="textSecondary">*/}
                    {/*adjective*/}
                {/*</Typography>*/}
                {seated &&
                    <Typography component="p">
                        {table.seats[1] !== "" &&
                        <span key={1}>
                                {users[table.seats[1]].userhtml} <SimpleStone size={15} id={table.player_color(1)}/>
                            </span>
                        } &nbsp; - &nbsp; 
                        {table.seats[2] !== "" &&
                        <span key={2}>
                                {users[table.seats[2]].userhtml} <SimpleStone size={15} id={table.player_color(2)}/>
                            </span>
                        }
                    </Typography>
                }
                <Typography component="p">
                    rated: {table.rated?'yes':'no'}, timed: {table.timed?(table.initialMinutes+'/'+table.incrementalSeconds):'no'}
                </Typography>
                
                {table.watching().length > 0 && 
                    <Typography component="p">
                        watching: {table.watching().map((player, i) => <span key={i}>{users[player].userhtml}, </span>)}
                    </Typography>
                }
            </CardContent>
        </Card>
    );
};
    {/*<Paper style={{width: '100%', height: '100%'}}>*/}
        {/*<Typography variant={'h1'} className={props.table.table_color()}>*/}
            {/**/}
        {/*</Typography>*/}
    {/*</Paper>*/}
{/*);*/}

TableCard.propTypes = {
    classes: PropTypes.object.isRequired,
    table: PropTypes.instanceOf(Table).isRequired,
    users: PropTypes.objectOf(
        PropTypes.instanceOf(User).isRequired
    ).isRequired
};

export default withStyles(styles)(TableCard);