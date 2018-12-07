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

import MenuIcon from './MenuIcon';
import SearchContainer from '../../../containers/Browser/Sidebar/SearchContainer';

const propTypes = {
  isShown: PropTypes.bool.isRequired,
  onToggle: PropTypes.func.isRequired,
  location: PropTypes.shape({}).isRequired,
};

const Sidebar = ({ isShown, onToggle, location }) => (
  <nav className="sidebar">
    <div className="sidebar__contents">
      <MenuIcon
        isActive={isShown}
        onToggle={onToggle}
      />
      <SearchContainer
        isActive={isShown}
        location={location}
      />
    </div>
  </nav>
);

Sidebar.propTypes = propTypes;

export default Sidebar;
