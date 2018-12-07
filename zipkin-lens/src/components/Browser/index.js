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
import { CSSTransition } from 'react-transition-group';
import queryString from 'query-string';

import Sidebar from './Sidebar';
import TracesContainer from '../../containers/Browser/Traces/TracesContainer';

const propTypes = {
  location: PropTypes.shape({}).isRequired,
  clearTraces: PropTypes.func.isRequired,
  fetchTraces: PropTypes.func.isRequired,
};

class Browser extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      isSidebarShown: true,
    };
    this.handleSidebarToggle = this.handleSidebarToggle.bind(this);
  }

  componentDidMount() {
    /* Trigger initial state change for CSSTransition */
    setTimeout(() => { this.setState({ isSidebarShown: false }); }, 0);

    const {
      location,
      fetchTraces,
    } = this.props;

    if (location.search !== '' && location.search !== '?') {
      const query = queryString.parse(location.search);
      fetchTraces(query);
    }
  }

  componentWillReceiveProps({ location }) {
    const {
      location: prevLocation,
      fetchTraces,
    } = this.props;

    if (location.search !== '' && location.search !== '?' && prevLocation.search !== location.search) {
      const query = queryString.parse(location.search);
      fetchTraces(query);
    }
  }

  componentWillUnmount() {
    const {
      clearTraces,
    } = this.props;

    clearTraces();
  }

  handleSidebarToggle() {
    const { isSidebarShown } = this.state;
    this.setState({ isSidebarShown: !isSidebarShown });
  }

  render() {
    const { location } = this.props;
    const { isSidebarShown } = this.state;

    return (
      <div>
        <CSSTransition
          in={isSidebarShown}
          classNames="browser__sidebar"
          timeout={500}
        >
          <Sidebar
            isShown={isSidebarShown}
            onToggle={this.handleSidebarToggle}
            location={location}
          />
        </CSSTransition>
        <CSSTransition
          in={isSidebarShown}
          classNames="browser__main"
          timeout={500}
        >
          <TracesContainer
            location={location}
          />
        </CSSTransition>
      </div>
    );
  }
}

Browser.propTypes = propTypes;

export default Browser;
