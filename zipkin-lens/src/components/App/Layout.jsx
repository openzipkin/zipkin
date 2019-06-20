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
import Box from '@material-ui/core/Box';
import Button from '@material-ui/core/Button';
import Typography from '@material-ui/core/Typography';
import Paper from '@material-ui/core/Paper';
import CssBaseline from '@material-ui/core/CssBaseline';

import Sidebar from './Sidebar';
import TraceId from './TraceId';
import GlobalSearch from '../GlobalSearch';

const useStyles = makeStyles({
  drawer: {
    width: '3.2rem',
    flexShrink: 0,
  },
  uploadButton: {
    marginTop: '8px', // for align with TraceID input.
    marginRight: '0.4rem',
    height: '1.8rem',
    width: '1.4rem',
    minWidth: '1.4rem',
  },
  contentPaper: {
    flex: '0 1 100%',
    marginTop: '1rem',
    marginBottom: '1rem',
    overflow: 'auto',
  },
});

const propTypes = {
  children: PropTypes.arrayOf(PropTypes.element).isRequired,
};

const Layout = ({ children }) => {
  const classes = useStyles();

  return (
    <Box display="flex">
      <CssBaseline />
      <nav className={classes.drawer}>
        <Sidebar />
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
            <TraceId />
          </Box>
        </Box>
        <Box pl={1} pr={2}>
          <GlobalSearch />
        </Box>
        <Paper className={classes.contentPaper}>
          <Box overflow="auto" width="100%" height="100%">
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
        </Paper>
      </Box>
    </Box>
  );
};

Layout.propTypes = propTypes;

export default withRouter(Layout);
