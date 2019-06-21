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
import { withRouter } from 'react-router';
import { makeStyles } from '@material-ui/styles';
import Box from '@material-ui/core/Box';
import Drawer from '@material-ui/core/Drawer';
import List from '@material-ui/core/List';
import ListItem from '@material-ui/core/ListItem';
import Tooltip from '@material-ui/core/Tooltip';
import grey from '@material-ui/core/colors/grey';

import { theme } from '../../colors';
import Logo from '../../img/zipkin-sm-logo.svg';

const useStyles = makeStyles({
  paper: {
    width: '3.2rem',
    backgroundColor: grey[900],
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'space-between',
  },
  listItem: {
    height: '3.2rem',
    cursor: 'pointer',
    fontSize: '1.05rem',
    color: theme.palette.grey[400],
    '&:hover': {
      color: theme.palette.common.white,
    },
  },
  logo: {
    marginTop: '0.8rem',
    marginBottom: '0.35rem',
    width: '2.2rem',
    height: '2.2rem',
    '& *': {
      fill: theme.palette.common.white,
    },
  },
});

const propTypes = {
  history: PropTypes.shape({ push: PropTypes.func.isRequired }).isRequired,
  location: PropTypes.shape({ search: PropTypes.string.isRequired }).isRequired,
};

const Sidebar = ({
  history,
  location,
}) => {
  const classes = useStyles();

  return (
    <Drawer
      variant="permanent"
      open
      classes={{
        paper: classes.paper,
      }}
    >
      <Box>
        <Box display="flex" justifyContent="center" width="100%">
          <Logo className={classes.logo} />
        </Box>
        <List>
          <Tooltip title="Traces" placement="right">
            <ListItem
              button
              className={classes.listItem}
              onClick={() => history.push('/zipkin')}
              style={
                location.pathname === '/zipkin'
                  ? {
                    color: theme.palette.common.white,
                    backgroundColor: theme.palette.primary.dark,
                  }
                  : null
              }
            >
              <Box component="span" className="fas fa-search" />
            </ListItem>
          </Tooltip>
          <Tooltip title="Dependency Links" placement="right">
            <ListItem
              button
              className={classes.listItem}
              onClick={() => history.push('/zipkin/dependency')}
              style={
                location.pathname === '/zipkin/dependency'
                  ? {
                    color: theme.palette.common.white,
                    backgroundColor: theme.palette.primary.dark,
                  }
                  : null
              }
            >
              <Box component="span" className="fas fa-project-diagram" />
            </ListItem>
          </Tooltip>
        </List>
      </Box>
      <List>
        <Tooltip title="Zipkin Home" placement="right">
          <ListItem
            button
            component="a"
            href="https://zipkin.io/"
            className={classes.listItem}
          >
            <Box component="span" className="fas fa-home" />
          </ListItem>
        </Tooltip>
        <Tooltip title="Repository" placement="right">
          <ListItem
            button
            component="a"
            href="https://github.com/openzipkin/zipkin"
            className={classes.listItem}
          >
            <Box component="span" className="fab fa-github" />
          </ListItem>
        </Tooltip>
        <Tooltip title="Twitter" placement="right">
          <ListItem
            button
            component="a"
            href="https://twitter.com/zipkinproject"
            className={classes.listItem}
          >
            <Box component="span" className="fab fa-twitter" />
          </ListItem>
        </Tooltip>
        <Tooltip title="Gitter" placement="right">
          <ListItem
            button
            component="a"
            href="https://gitter.im/openzipkin/zipkin/"
            className={classes.listItem}
          >
            <Box component="span" className="fab fa-gitter" />
          </ListItem>
        </Tooltip>
      </List>
    </Drawer>
  );
};

Sidebar.propTypes = propTypes;

export default withRouter(Sidebar);
