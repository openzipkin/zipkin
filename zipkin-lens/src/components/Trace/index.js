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

import TraceInfo from './TraceInfo';
import Timeline from '../Timeline';
import LoadingOverlay from '../Common/LoadingOverlay';
import MiniTraceViewer from './MiniTraceViewer';

const propTypes = {
  isLoading: PropTypes.bool.isRequired,
  traceId: PropTypes.string.isRequired, /* From url parameter */
  traceSummary: PropTypes.shape({}),

  fetchTrace: PropTypes.func.isRequired,
};

const defaultProps = {
  traceSummary: null,
};

class Trace extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      startTs: null,
      endTs: null,
    };
    this.handleStartAndEndTsChange = this.handleStartAndEndTsChange.bind(this);
  }

  componentDidMount() {
    this.ensureTraceFetched();
  }

  ensureTraceFetched() {
    const {
      fetchTrace,
      traceId,
      traceSummary,
    } = this.props;
    if (!traceSummary || traceSummary.traceId !== traceId) {
      fetchTrace(traceId);
    }
  }

  handleStartAndEndTsChange(startTs, endTs) {
    this.setState({
      startTs,
      endTs,
    });
  }

  render() {
    const {
      startTs,
      endTs,
    } = this.state;

    const {
      isLoading,
      traceId,
      traceSummary,
    } = this.props;

    return (
      <div>
        <LoadingOverlay active={isLoading} />
        <div className="trace">
          {
            (!traceSummary || traceSummary.traceId !== traceId)
              ? null
              : (
                <div>
                  <div className="trace__trace-info-wrapper">
                    <TraceInfo
                      traceSummary={traceSummary}
                    />
                  </div>
                  <div className="trace__mini-trace-viewer-wrapper">
                    <MiniTraceViewer
                      startTs={startTs || 0}
                      endTs={endTs || traceSummary.duration}
                      traceSummary={traceSummary}
                      onStartAndEndTsChange={this.handleStartAndEndTsChange}
                    />
                  </div>
                  <Timeline
                    startTs={startTs || 0}
                    endTs={endTs || traceSummary.duration}
                    traceSummary={traceSummary}
                  />
                </div>
              )
          }
        </div>
      </div>
    );
  }
}

Trace.propTypes = propTypes;
Trace.defaultProps = defaultProps;

export default Trace;
