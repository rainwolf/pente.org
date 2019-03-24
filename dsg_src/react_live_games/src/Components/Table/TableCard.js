import React from 'react';
import PropTypes from 'prop-types';
import Table from '../../redux_reducers/TableClass';
import User from '../../redux_reducers/UserClass';
import Typography from '@material-ui/core/Typography';
import Card from '@material-ui/core/Card';
import CardContent from '@material-ui/core/CardContent';

import classNames from 'classnames';
import { withStyles } from '@material-ui/core/styles';


const styles = {
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
};

const TableCard = (props) => {
    const { classes, table, users } = props;
    
    return (
        <Card className={classes.card} style={{backgroundColor: table.table_color()}}>
            <CardContent>
                {/*<Typography className={classes.title} color="textSecondary" gutterBottom>*/}
                    {/*Word of the Day*/}
                {/*</Typography>*/}
                <Typography variant="h5" component="h2">
                    {table.game_name()}
                </Typography>
                {/*<Typography className={classes.pos} color="textSecondary">*/}
                    {/*adjective*/}
                {/*</Typography>*/}
                {/*<Typography component="p">*/}
                
                {/*</Typography>*/}
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