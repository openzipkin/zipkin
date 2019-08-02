/*
 * Copyright 2015-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
import PropTypes from 'prop-types';
import React from 'react';
import { makeStyles } from '@material-ui/styles';
import Box from '@material-ui/core/Box';
import SnackbarContent from '@material-ui/core/SnackbarContent';
import Zoom from '@material-ui/core/Zoom';

const propTypes = {
  variant: PropTypes.string.isRequired,
  message: PropTypes.string.isRequired,
};

const useStyles = makeStyles(theme => ({
  error: {
    backgroundColor: theme.palette.error.dark,
  },
  info: {
    backgroundColor: theme.palette.primary.dark,
  },
}));

const MessageBar = ({ variant, message }) => {
  const classes = useStyles();

  return (
    <Zoom in>
      <SnackbarContent
        className={classes[variant]}
        message={(
          <Box component="span">
            <Box component="span" mr={2} className="fas fa-exclamation" />
            {message}
          </Box>
        )}
      />
    </Zoom>
  );
};

MessageBar.propTypes = propTypes;

export default MessageBar;
