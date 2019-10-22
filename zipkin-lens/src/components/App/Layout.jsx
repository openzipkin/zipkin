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
import Box from '@material-ui/core/Box';
import CssBaseline from '@material-ui/core/CssBaseline';

import Sidebar from './Sidebar';

const propTypes = {
  children: PropTypes.arrayOf(PropTypes.element).isRequired,
};

const Layout = ({ children }) => (
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
      overflow="auto"
    >
      {children}
    </Box>
  </Box>
);


Layout.propTypes = propTypes;

export default Layout;
