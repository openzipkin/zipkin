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
import Typography from '@material-ui/core/Typography';
import Paper from '@material-ui/core/Paper';
import CssBaseline from '@material-ui/core/CssBaseline';

import Sidebar from './Sidebar';
import TraceIdSearchInput from './TraceIdSearchInput';
import TraceJsonUploader from './TraceJsonUploader';
import GlobalSearch from '../GlobalSearch';

const useStyles = makeStyles({
  contentPaper: {
    flex: '0 1 100%',
    marginTop: '1.5rem',
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
      <Box component="nav" width="3.2rem" flexShrink="0">
        <Sidebar />
      </Box>
      <Box
        component="main"
        display="flex"
        flexDirection="column"
        height="100vh"
        width="100%"
        pl={3}
        pr={3}
        overflow="hidden"
      >
        <Box width="100%" display="flex" justifyContent="space-between">
          <Box display="flex" alignItems="center">
            <Typography variant="h5">
              Discover
            </Typography>
          </Box>
          <Box pr={4} display="flex" alignItems="center">
            <TraceJsonUploader />
            <TraceIdSearchInput />
          </Box>
        </Box>
        <GlobalSearch />
        <Paper className={classes.contentPaper}>
          <Box overflow="auto" height="100%">
            <AutoSizer>
              {
                ({ height, width }) => (
                  <Box height={height} width={width} overflow="auto">
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
