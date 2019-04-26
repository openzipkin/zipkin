/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import PropTypes from 'prop-types';
import React from 'react';
import { withRouter } from 'react-router';

import Sidebar from './Sidebar';
import GlobalSearchContainer from '../../containers/GlobalSearch/GlobalSearchContainer';

const propTypes = {
  location: PropTypes.shape({}).isRequired,
  children: PropTypes.arrayOf(PropTypes.element).isRequired,
};

const Layout = ({ location, children }) => (
  <div className="app__layout">
    <Sidebar location={location} />
    <div className="app__global-search-wrapper">
      <GlobalSearchContainer />
    </div>
    <div className="app__content">
      {children}
    </div>
  </div>
);

Layout.propTypes = propTypes;

export default withRouter(Layout);
