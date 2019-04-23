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
