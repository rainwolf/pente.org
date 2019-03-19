import React from 'react';
import TextField from '@material-ui/core/TextField';
import PropTypes from 'prop-types';

const ChatInput = (props) => (
    <TextField
        id="filled-full-width"
        label="Talk here!"
        style={{ margin: 8 }}
        placeholder="Press Enter to send"
        // helperText="Full width!"
        fullWidth
        margin="normal"
        variant="filled"
        InputLabelProps={{
            shrink: true,
        }}
        onKeyDown={props.sendHandler}
    />
);

ChatInput.propTypes = {
    sendHandler: PropTypes.func.isRequired
};

export default ChatInput;