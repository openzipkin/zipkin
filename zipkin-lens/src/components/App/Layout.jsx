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
import { AutoSizer } from 'react-virtualized';
import { makeStyles } from '@material-ui/styles';
import grey from '@material-ui/core/colors/grey';
import Box from '@material-ui/core/Box';
import Button from '@material-ui/core/Button';
import Drawer from '@material-ui/core/Drawer';
import TextField from '@material-ui/core/TextField';
import Typography from '@material-ui/core/Typography';

import Sidebar from './Sidebar';
import GlobalSearch from '../GlobalSearch';

const useStyles = makeStyles(theme => ({
  drawer: {
    width: '3rem',
    flexShrink: 0,
  },
  drawerPaper: {
    color: theme.palette.common.white,
    width: '3rem',
    backgroundColor: grey[900],
  },
  traceIdInput: {
    fontSize: '0.8rem',
    height: '1.4rem',
    padding: '0.2rem 0.2rem',
  },
  uploadButton: {
    marginTop: '8px', // for align with TraceID input.
    marginRight: '0.4rem',
    height: '1.8rem',
    width: '1.4rem',
    minWidth: '1.4rem',
  },
}));

const propTypes = {
  location: PropTypes.shape({}).isRequired,
  children: PropTypes.arrayOf(PropTypes.element).isRequired,
};

const Layout = ({ location, children }) => {
  const classes = useStyles();

  return (
    <Box display="flex">
      <nav className={classes.drawer}>
        <Drawer
          variant="permanent"
          open
          classes={{
            paper: classes.drawerPaper,
          }}
        >
          <Sidebar />
        </Drawer>
      </nav>
      <Box
        component="main"
        display="flex"
        flexDirection="column"
        height="100vh"
        width="100%"
        pl={1}
        pr={1}
        overflow="hidden"
      >
        <Box
          width="100%"
          display="flex"
          justifyContent="space-between"
        >
          <Box
            display="flex"
            alignItems="center"
          >
            <Typography variant="h5">
              Discover
            </Typography>
          </Box>
          <Box
            pr={4}
            display="flex"
            alignItems="center"
          >
            <Button variant="outlined" className={classes.uploadButton}>
              <Box component="span" className="fas fa-upload" />
            </Button>
            <TextField
              id="standard-name"
              label="Trace ID"
              className={classes.textField}
              value="hello"
              margin="normal"
              variant="outlined"
              InputProps={{
                classes: { input: classes.traceIdInput },
              }}
            />
          </Box>
        </Box>
        <Box pl={1} pr={2}>
          <GlobalSearch />
        </Box>
        <Box
          flex="0 1 100%"
          mb={3}
          overflow="auto"
        >
          <AutoSizer>
            {
              ({ height, width }) => (
                <Box
                  height={height}
                  width={width}
                  overflow="auto"
                >
                  {children}
                </Box>
              )
            }
          </AutoSizer>
        </Box>
      </Box>
    </Box>
  );
};

/*
const Layout = ({ location, children }) => (
  <div className="app__layout">
    <Sidebar location={location} />
    <div className="app__header">
      <div className="app__global-search-wrapper">
        <GlobalSearch />
      </div>
      <div className="app__global-menu-wrapper">
        <GlobalMenu />
      </div>
    </div>
    <div className="app__content">
      {children}
    </div>
  </div>
);
*/

Layout.propTypes = propTypes;

export default withRouter(Layout);
