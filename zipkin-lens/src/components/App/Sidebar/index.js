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
import Cookies from 'js-cookie';

import SidebarPageOption from './SidebarPageOption';
import Logo from '../../../img/zipkin-logo.svg';

const propTypes = {
  location: PropTypes.shape({ pathname: PropTypes.string }).isRequired,
  history: PropTypes.shape({ push: PropTypes.func.isRequired }).isRequired,
};

class Sidebar extends React.Component {
  constructor(props) {
    super(props);
    this.goBackToClassic = this.goBackToClassic.bind(this);
  }

  goBackToClassic(event) {
    const { location, history } = this.props;

    Cookies.remove('lens');
    if (location.pathname === '/zipkin') {
      history.push('/zipkin/');
    } else {
      history.push(`${location.pathname}`);
    }
    window.location.reload(true);
    event.preventDefault();
  }

  render() {
    const { location } = this.props;
    return (
      <div className="sidebar">
        <div
          to={{ pathname: '' }}
          className="sidebar__brand-link"
        >
          <Logo className="sidebar__brand-logo" />
        </div>
        <div className="sidebar__menu">
          <SidebarPageOption location={location} pageName="browser" />
          <SidebarPageOption location={location} pageName="dependencies" />
        </div>
        {
          Cookies.get('lens')
            ? (
              <div className="sidebar__go-back-to-classic-button-wrapper">
                <button
                  type="button"
                  className="sidebar__go-back-to-classic-button"
                  onClick={this.goBackToClassic}
                >
                  Go back to classic Zipkin
                </button>
              </div>
            )
            : null
        }
        <div className="sidebar__other-links">
          <a href="https://zipkin.apache.org/" target="_blank" rel="noopener noreferrer">
            <div className="sidebar__other-link fas fa-home" />
          </a>
          <a href="https://github.com/openzipkin/zipkin" target="_blank" rel="noopener noreferrer">
            <div className="sidebar__other-link fab fa-github" />
          </a>
          <a href="https://twitter.com/zipkinproject" target="_blank" rel="noopener noreferrer">
            <div className="sidebar__other-link fab fa-twitter" />
          </a>
          <a href="https://gitter.im/openzipkin/zipkin/" target="_blank" rel="noopener noreferrer">
            <div className="sidebar__other-link fab fa-gitter" />
          </a>
        </div>
      </div>
    );
  }
}

Sidebar.propTypes = propTypes;

export default withRouter(Sidebar);
