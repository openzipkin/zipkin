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

import Timeline from '../Timeline';
import MiniTimeline from '../MiniTimeline';
import { detailedTraceSummaryPropTypes } from '../../prop-types';

const propTypes = {
  startTs: PropTypes.number,
  endTs: PropTypes.number,
  onStartAndEndTsChange: PropTypes.func.isRequired,
  traceSummary: detailedTraceSummaryPropTypes.isRequired,
};

const defaultProps = {
  startTs: null,
  endTs: null,
};

class DetailedTraceSummary extends React.Component {
  renderHeader() {
    const { traceSummary } = this.props;
    const {
      durationStr,
      serviceNameAndSpanCounts,
      spans,
      depth,
    } = traceSummary;

    return (
      <div className="detailed-trace-summary__header">
        <div className="detailed-trace-summary__trace-id">
          {traceSummary.traceId}
        </div>
        <div className="detailed-trace-summary__trace-data-list">
          {
            [
              { label: 'Duration', value: durationStr },
              { label: 'Services', value: serviceNameAndSpanCounts.length },
              { label: 'Depth', value: depth },
              { label: 'Total Spans', value: spans.length },
            ].map(elem => (
              <div
                key={elem.label}
                className="detailed-trace-summary__data-wrapper"
              >
                <div className="detailed-trace-summary__data-label">
                  {elem.label}
                </div>
                <div className="detailed-trace-summary__data-value">
                  {elem.value}
                </div>
              </div>
            ))
          }
        </div>
      </div>
    );
  }

  render() {
    const {
      startTs,
      endTs,
      onStartAndEndTsChange,
      traceSummary,
    } = this.props;

    return (
      <div className="detailed-trace-summary">
        {this.renderHeader()}
        <div className="detailed-trace-summary__mini-trace-viewer-wrapper">
          <MiniTimeline
            startTs={startTs || 0}
            endTs={endTs || traceSummary.duration}
            traceSummary={traceSummary}
            onStartAndEndTsChange={onStartAndEndTsChange}
          />
        </div>
        <div className="detailed-trace-summary__timeline-wrapper">
          <Timeline
            startTs={startTs || 0}
            endTs={endTs || traceSummary.duration}
            traceSummary={traceSummary}
          />
        </div>
      </div>
    );
  }
}

DetailedTraceSummary.propTypes = propTypes;
DetailedTraceSummary.defaultProps = defaultProps;

export default DetailedTraceSummary;
