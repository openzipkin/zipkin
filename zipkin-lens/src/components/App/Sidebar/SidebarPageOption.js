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
import { Link } from 'react-router-dom';

const propTypes = {
  location: PropTypes.shape({
    pathname: PropTypes.string.isRequired,
  }).isRequired,
  pageName: PropTypes.string.isRequired,
};

const pageData = {
  browser: {
    url: '/zipkin',
    label: 'Search',
    icon: 'fas fa-search',
  },
  dependencies: {
    url: '/zipkin/dependency',
    label: 'Dependencies',
    icon: 'fas fa-code-branch',
  },
};

const SidebarPageOption = ({ location, pageName }) => {
  const isSelected = location.pathname === pageData[pageName].url;
  return (
    <div className={`sidebar__page-option ${isSelected ? 'sidebar__page-option--selected' : ''}`}>
      <Link to={{ pathname: pageData[pageName].url }}>
        <div className="sidebar__page-option-icon">
          <i className={pageData[pageName].icon} />
        </div>
        <div className="sidebar__page-option-label">
          {pageData[pageName].label}
        </div>
      </Link>
    </div>
  );
};

SidebarPageOption.propTypes = propTypes;

export default SidebarPageOption;
