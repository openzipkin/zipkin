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

import DetailedTraceSummary from '../DetailedTraceSummary';
import { detailedTraceSummaryPropTypes } from '../../prop-types';

const propTypes = {
  traceSummary: detailedTraceSummaryPropTypes,
  isMalformedFile: PropTypes.bool.isRequired,
  errorMessage: PropTypes.string.isRequired,
};

const defaultProps = {
  traceSummary: null,
};

class TraceViewer extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      startTs: null,
      endTs: null,
    };
    this.handleStartAndEndTsChange = this.handleStartAndEndTsChange.bind(this);
  }

  handleStartAndEndTsChange(startTs, endTs) {
    this.setState({ startTs, endTs });
  }

  render() {
    const { startTs, endTs } = this.state;
    const { traceSummary, isMalformedFile, errorMessage } = this.props;

    if (isMalformedFile) {
      return (
        <div className="trace-viewer__error-message-wrapper">
          <div className="trace-viewer__error-message">
            Error:
            &nbsp;
            {errorMessage}
          </div>
        </div>
      );
    }

    return (
      <div className="trace-viewer">
        {
          traceSummary ? (
            <DetailedTraceSummary
              startTs={startTs}
              endTs={endTs}
              onStartAndEndTsChange={this.handleStartAndEndTsChange}
              traceSummary={traceSummary}
            />
          ) : null
        }
      </div>
    );
  }
}

TraceViewer.propTypes = propTypes;
TraceViewer.defaultProps = defaultProps;

export default TraceViewer;
