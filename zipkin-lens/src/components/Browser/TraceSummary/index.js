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

import TraceSummaryBar from './TraceSummaryBar';
import TraceSummaryUpperBar from './TraceSummaryUpperBar';
import TraceSummaryButtons from './TraceSummaryButtons';
import Timeline from '../../Timeline';
import ServiceNameBadge from '../../Common/ServiceNameBadge';
import { detailedTraceSummary } from '../../../zipkin';
import { traceSummaryPropTypes } from '../../../prop-types';

const propTypes = {
  traceSummary: traceSummaryPropTypes.isRequired,
  skewCorrectedTrace: PropTypes.shape({}).isRequired,
};

class TraceSummary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { isTimelineOpened: false };
    this.handleTimelineOpenToggle = this.handleTimelineOpenToggle.bind(this);
  }

  handleTimelineOpenToggle(event) {
    const { isTimelineOpened } = this.state;
    this.setState({ isTimelineOpened: !isTimelineOpened });
    event.stopPropagation();
  }

  render() {
    const { traceSummary, skewCorrectedTrace } = this.props;
    const { isTimelineOpened } = this.state;

    const detailedTrace = detailedTraceSummary(skewCorrectedTrace);

    return (
      <div className="trace-summary">
        <div
          className="trace-summary__summary"
          role="presentation"
          onClick={this.handleTimelineOpenToggle}
          data-test="summary"
        >
          <div className="trace-summary__trace-id">
            Trace ID:&nbsp;
            <b>
              {traceSummary.traceId}
            </b>
          </div>
          <div className="trace-summary__bars-and-buttons">
            <div className="trace-summary__bars">
              <TraceSummaryUpperBar traceSummary={traceSummary} />
              {
                traceSummary.servicePercentage != null && typeof traceSummary.servicePercentage !== 'undefined'
                  ? (
                    <TraceSummaryBar
                      width={traceSummary.servicePercentage}
                      infoClass={traceSummary.infoClass}
                    >
                      {traceSummary.servicePercentage}
                      %
                    </TraceSummaryBar>
                  )
                  : null
              }
            </div>
            <TraceSummaryButtons traceId={traceSummary.traceId} />
          </div>
          <div className="trace-summary__service-badges">
            {
              traceSummary.serviceSummaries.map(serviceSummary => (
                <div key={serviceSummary.serviceName} className="trace-summary__badge-wrapper">
                  <ServiceNameBadge
                    serviceName={serviceSummary.serviceName}
                    count={serviceSummary.spanCount}
                  />
                </div>
              ))
            }
          </div>
        </div>
        {
          isTimelineOpened
            ? (
              <div className="trace-summary__timeline-wrapper">
                <Timeline
                  startTs={0}
                  endTs={detailedTrace.duration}
                  traceSummary={detailedTrace}
                />
              </div>
            )
            : null
        }
      </div>
    );
  }
}

TraceSummary.propTypes = propTypes;

export default TraceSummary;
