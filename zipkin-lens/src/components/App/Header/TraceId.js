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
import { withRouter } from 'react-router';

import Input from '../../Common/Input';

const propTypes = {
  history: PropTypes.shape({}).isRequired,
};

class TraceId extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      traceId: '',
    };
    this.handleTextChange = this.handleTextChange.bind(this);
    this.handleKeyPress = this.handleKeyPress.bind(this);
  }

  handleTextChange(traceId) {
    this.setState({
      traceId,
    });
  }

  handleKeyPress(e) {
    const { history } = this.props;
    const { traceId } = this.state;
    if (e.key === 'Enter') {
      history.push(`/zipkin/trace/${traceId}`);
    }
  }

  render() {
    const { traceId } = this.state;
    return (
      <Input
        className="header__trace-id"
        placeholder="Go to trace"
        value={traceId}
        onChange={this.handleTextChange}
        onKeyPress={this.handleKeyPress}
      />
    );
  }
}

TraceId.propTypes = propTypes;

export default withRouter(TraceId);
