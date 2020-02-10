/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
import React from 'react';
import { faSearch } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import Box from '@material-ui/core/Box';
import Typography from '@material-ui/core/Typography';
import { makeStyles } from '@material-ui/styles';
import { FormattedMessage } from 'react-intl';

import messages from './messages';

const useStyles = makeStyles(theme => ({
  root: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    color: theme.palette.text.secondary,
  },
  iconWrapper: {
    fontSize: '10rem',
    lineHeight: '1',
  },
  description: {
    marginTop: theme.spacing(1.4),
    textAlign: 'center',
    whiteSpace: 'pre-line',
  },
}));

const ExplainBox = React.memo(() => {
  const classes = useStyles();

  return (
    <Box className={classes.root}>
      <Box className={classes.iconWrapper}>
        <FontAwesomeIcon icon={faSearch} />
      </Box>
      <Typography variant="h4">
        <FormattedMessage {...messages.searchTracesHeader} />
      </Typography>
      <Typography variant="body1" className={classes.description}>
        <FormattedMessage {...messages.searchTracesDescription} />
      </Typography>
    </Box>
  );
});

export default ExplainBox;
