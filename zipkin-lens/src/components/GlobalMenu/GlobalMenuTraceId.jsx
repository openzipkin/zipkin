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

const propTypes = {
  history: PropTypes.shape({ push: PropTypes.func.isRequired }).isRequired,
};

class GlobalMenuTraceId extends React.Component {
  constructor(props) {
    super(props);
    this.state = { traceId: '' };
    this.handleChange = this.handleChange.bind(this);
    this.handleFindButtonClick = this.handleFindButtonClick.bind(this);
  }

  handleChange(event) {
    this.setState({ traceId: event.target.value });
  }

  handleFindButtonClick(event) {
    const { history } = this.props;
    const { traceId } = this.state;
    history.push({ pathname: `/zipkin/traces/${traceId}` });
    event.stopPropagation();
  }

  render() {
    const { traceId } = this.state;

    return (
      <div className="global-menu-trace-id">
        <div className="global-menu-trace-id__label">
          Trace ID
        </div>
        <div className="global-menu-trace-id__content">
          <input
            className="global-menu-trace-id__input"
            type="text"
            value={traceId}
            onChange={this.handleChange}
          />
          <div className="global-menu-trace-id__find-button-wrapper">
            <button
              type="button"
              className="global-menu-trace-id__find-button"
              onClick={this.handleFindButtonClick}
            >
              <span className="fas fa-search global-menu-trace-id__find-button-icon" />
            </button>
          </div>
        </div>

      </div>
    );
  }
}

GlobalMenuTraceId.propTypes = propTypes;

export default withRouter(GlobalMenuTraceId);
