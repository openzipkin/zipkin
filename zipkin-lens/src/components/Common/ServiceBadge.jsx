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
import React, { useMemo } from 'react';
import { makeStyles, ThemeProvider } from '@material-ui/styles';
import Box from '@material-ui/core/Box';
import Paper from '@material-ui/core/Paper';

import { selectServiceTheme } from '../../colors';

const useStyles = makeStyles(theme => ({
  root: {
    overflow: 'hidden',
    display: 'flex',
  },
  buttonBase: {
    height: '1.8rem',
    paddingRight: '0.5rem',
    paddingLeft: '0.5rem',
    display: 'flex',
    alignItems: 'center',
    textTransform: 'uppercase',
    color: theme.palette.common.white,
    backgroundColor: theme.palette.primary.dark,
  },
  clickableButton: {
    cursor: 'pointer',
    transition: theme.transitions.create(['background-color'], {
      duration: theme.transitions.duration.short,
    }),
    '&:hover': {
      backgroundColor: theme.palette.primary.main,
    },
  },
}));

const propTypes = {
  serviceName: PropTypes.string.isRequired,
  count: PropTypes.number,
  onClick: PropTypes.func,
  onDelete: PropTypes.func,
};

const defaultProps = {
  count: null,
  onClick: null,
  onDelete: null,
};

const ServiceBadgeImpl = ({
  serviceName,
  count,
  onClick,
  onDelete,
}) => {
  const classes = useStyles();

  const label = useMemo(
    () => `${serviceName}${count ? ` (${count})` : ''}`,
    [count, serviceName],
  );

  if (!onClick) {
    return (
      <Paper className={classes.root}>
        <Box
          className={classes.buttonBase}
          component="span"
          data-test="unclickable-badge"
        >
          {label}
        </Box>
      </Paper>
    );
  }

  return (
    <Paper className={classes.root}>
      <Box
        className={`${classes.buttonBase} ${classes.clickableButton}`}
        onClick={onClick}
        data-test="clickable-badge"
      >
        {label}
      </Box>
      {
        onDelete ? (
          <Box
            className={`${classes.buttonBase} ${classes.clickableButton}`}
            onClick={onDelete}
            data-test="delete-button"
          >
            <Box component="span" className="fas fa-times" />
          </Box>
        ) : null
      }
    </Paper>
  );
};

ServiceBadgeImpl.propTypes = propTypes;
ServiceBadgeImpl.defaultProps = defaultProps;

const ServiceBadge = ({ serviceName, ...props }) => {
  const theme = selectServiceTheme(serviceName);

  return (
    <ThemeProvider theme={theme}>
      <ServiceBadgeImpl serviceName={serviceName} {...props} />
    </ThemeProvider>
  );
};

ServiceBadge.propTypes = propTypes;
ServiceBadge.defaultProps = defaultProps;

export default ServiceBadge;
