/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import PropTypes from 'prop-types';
import React, { useMemo, useCallback } from 'react';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { makeStyles, ThemeProvider } from '@material-ui/styles';
import Box from '@material-ui/core/Box';
import Paper from '@material-ui/core/Paper';

import { selectServiceTheme } from '../../constants/color';

const useStyles = makeStyles((theme) => ({
  paper: {
    overflow: 'hidden',
    display: 'flex',
    flexShrink: 0,
  },
  buttonBase: {
    height: '1.8rem',
    paddingRight: '0.5rem',
    paddingLeft: '0.5rem',
    display: 'flex',
    alignItems: 'center',
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

const ServiceBadgeImpl = ({ serviceName, count, onClick, onDelete }) => {
  const classes = useStyles();

  const label = useMemo(
    () => `${serviceName}${count ? ` (${count})` : ''}`,
    [count, serviceName],
  );

  const handleClick = useCallback(() => {
    onClick(serviceName);
  }, [onClick, serviceName]);

  return (
    <Paper className={classes.paper}>
      <Box
        className={`${classes.buttonBase} ${
          onClick ? classes.clickableButton : ''
        }`}
        onClick={handleClick}
        data-testid="badge"
      >
        {label}
      </Box>
      {onDelete ? (
        <Box
          className={`${classes.buttonBase} ${classes.clickableButton}`}
          onClick={onDelete}
          data-testid="delete-button"
        >
          <FontAwesomeIcon icon={faTimes} />
        </Box>
      ) : null}
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
