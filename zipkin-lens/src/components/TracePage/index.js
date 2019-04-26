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

import LoadingOverlay from '../Common/LoadingOverlay';
import DetailedTraceSummary from '../DetailedTraceSummary';
import { detailedTraceSummaryPropTypes } from '../../prop-types';

const propTypes = {
  isLoading: PropTypes.bool.isRequired,
  traceId: PropTypes.string.isRequired,
  traceSummary: detailedTraceSummaryPropTypes,
  fetchTrace: PropTypes.func.isRequired,
};

const defaultProps = {
  traceSummary: null,
};

class TracePage extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      startTs: null,
      endTs: null,
    };
    this.handleStartAndEndTsChange = this.handleStartAndEndTsChange.bind(this);
  }

  componentDidMount() {
    const { fetchTrace, traceId, traceSummary } = this.props;
    if (!traceSummary || traceSummary.traceId !== traceId) {
      fetchTrace(traceId);
    }
  }

  handleStartAndEndTsChange(startTs, endTs) {
    this.setState({ startTs, endTs });
  }

  render() {
    const { startTs, endTs } = this.state;
    const { isLoading, traceId, traceSummary } = this.props;
    return (
      <div className="trace-page">
        <LoadingOverlay active={isLoading} />
        <div className="trace-page__detailed-trace-summary-wrapper">
          {
            (!traceSummary || traceSummary.traceId !== traceId)
              ? null
              : (
                <DetailedTraceSummary
                  startTs={startTs}
                  endTs={endTs}
                  onStartAndEndTsChange={this.handleStartAndEndTsChange}
                  traceSummary={traceSummary}
                />
              )
          }
        </div>
      </div>
    );
  }
}

TracePage.propTypes = propTypes;
TracePage.defaultProps = defaultProps;

export default TracePage;
