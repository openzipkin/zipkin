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
import { Link } from 'react-router-dom';

import TraceId from './TraceId';

const propTypes = {
  location: PropTypes.shape({}).isRequired,
};

const Header = ({ location }) => {
  const isBrowserSelected = location.pathname === '/zipkin';
  const isDependenciesSelected = location.pathname === '/zipkin/dependencies';

  return (
    <header className="header">
      <div className="header__contents">
        <div className="header__brand">
          <Link to={{ pathname: '/zipkin' }}>
            <div className="header__brand-title">
              Zipkin Lens
            </div>
          </Link>
        </div>
        <div className="header__menu">
          <div className={`header__option ${isBrowserSelected ? 'selected' : ''}`}>
            <Link
              to={{ pathname: '/zipkin' }}
              className={`header__option-link ${isBrowserSelected ? 'selected' : ''}`}
            >
              <i className="fas fa-search header__option-icon" />
              Search
            </Link>
          </div>
          <div className={`header__option ${isDependenciesSelected ? 'selected' : ''}`}>
            <Link
              to={{ pathname: '/zipkin/dependencies' }}
              className={`header__option-link ${isDependenciesSelected ? 'selected' : ''}`}
            >
              <i className="fas fa-code-branch header__option-icon" />
              Dependencies
            </Link>
          </div>
        </div>
        <TraceId />
      </div>
    </header>
  );
};

Header.propTypes = propTypes;

export default Header;
