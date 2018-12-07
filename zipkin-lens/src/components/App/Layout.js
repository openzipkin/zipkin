/*
 * Copyright 2018 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

import PropTypes from 'prop-types';
import React from 'react';

import Header from './Header';

const propTypes = {
  location: PropTypes.shape({}).isRequired,
  children: PropTypes.element.isRequired,
};

const Layout = ({ location, children }) => (
  <div className="app__layout">
    <Header location={location} />
    <div className="app__content">
      {children}
    </div>
  </div>
);

Layout.propTypes = propTypes;

export default Layout;
