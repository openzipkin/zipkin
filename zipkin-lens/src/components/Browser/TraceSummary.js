import PropTypes from 'prop-types';
import React from 'react';
import moment from 'moment';
import { Link } from 'react-router-dom';

import Timeline from '../Timeline';
import ServiceNameBadge from '../Common/ServiceNameBadge';
import { getErrorTypeColor } from '../../util/color';
import * as api from '../../constants/api';
import { detailedTraceSummary } from '../../zipkin';
import { traceSummaryPropTypes } from '../../prop-types';

const propTypes = {
  traceSummary: traceSummaryPropTypes.isRequired,
  skewCorrectedTrace: PropTypes.shape({}).isRequired,
};

class TraceSummary extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      isTimelineOpened: false,
    };
    this.handleTimelineOpenToggle = this.handleTimelineOpenToggle.bind(this);
  }

  handleTimelineOpenToggle(event) {
    const { isTimelineOpened } = this.state;
    this.setState({ isTimelineOpened: !isTimelineOpened });
    event.stopPropagation();
  }

  barColor() {
    const { traceSummary } = this.props;
    switch (traceSummary.infoClass) {
      case 'trace-error-transient':
        return getErrorTypeColor('transient');
      case 'trace-error-critical':
        return getErrorTypeColor('critical');
      default:
        return getErrorTypeColor('none');
    }
  }

  renderBar(width, label) {
    return (
      <div className="trace-summary__bar-container">
        <div
          className="trace-summary__bar-wrapper"
          style={{ width: `${width}%` }}
        >
          <div
            className="trace-summary__bar"
            style={{ backgroundColor: this.barColor() }}
          />
        </div>
        <div className="trace-summary__bar-label">
          {label}
        </div>
      </div>
    );
  }

  renderButtons() {
    const { traceSummary } = this.props;
    const { isTimelineOpened } = this.state;
    return (
      <div className="trace-summary__buttons">
        <span
          className={`fas fa-angle-double-${isTimelineOpened ? 'up' : 'down'} trace-summary__button`}
          role="presentation"
          onClick={this.handleTimelineOpenToggle}
        />
        <a href={`${api.TRACE}/${traceSummary.traceId}`} target="_brank">
          <i className="fas fa-file-download" />
        </a>
        <Link to={{ pathname: `/zipkin/traces/${traceSummary.traceId}` }}>
          <i className="fas fa-angle-double-right" />
        </Link>
      </div>
    );
  }

  renderServiceBadges() {
    const { traceSummary } = this.props;
    return traceSummary.serviceSummaries.map(serviceSummary => (
      <div
        key={serviceSummary.serviceName}
        className="trace-summary__badge-wrapper"
      >
        <ServiceNameBadge
          serviceName={serviceSummary.serviceName}
          count={serviceSummary.spanCount}
        />
      </div>
    ));
  }

  renderTimeline() {
    const { skewCorrectedTrace } = this.props;
    const { isTimelineOpened } = this.state;

    if (!isTimelineOpened) {
      return null;
    }
    const detailedTrace = detailedTraceSummary(skewCorrectedTrace);
    return (
      <div className="trace-summary__timeline-wrapper">
        <Timeline
          startTs={0}
          endTs={detailedTrace.duration}
          traceSummary={detailedTrace}
        />
      </div>
    );
  }

  render() {
    const { traceSummary } = this.props;

    const upperBarLabel = (
        <div className="trace-summary__upper-bar-label">
        <div className="trace-summary__upper-bar-label-meta">
          <div className="trace-summary__upper-bar-label-duration">{`${traceSummary.durationStr}`}</div>
          <div className="trace-summary__upper-bar-label-spans">&nbsp;{`- ${traceSummary.spanCount} spans`}</div>
        </div>
        <div>
          {moment(traceSummary.timestamp / 1000).format('MM/DD HH:mm:ss:SSS')}
        </div>
      </div>
    );
    const lowerBarLabel = `${traceSummary.servicePercentage}%`;

    return (
      <div className="trace-summary">
        <div
          className="trace-summary__summary"
          role="presentation"
          onClick={this.handleTimelineOpenToggle}
        >
          <div className="trace-summary__trace-id">
            Trace ID:&nbsp;
            <b>
              {traceSummary.traceId}
            </b>
          </div>
          <div className="trace-summary__upper-container">
            <div className="trace-summary__bars">
              {this.renderBar(traceSummary.width, upperBarLabel)}
              {
                traceSummary.servicePercentage
                  ? this.renderBar(traceSummary.servicePercentage, lowerBarLabel)
                  : null
              }
            </div>
            {this.renderButtons()}
          </div>
          <div className="trace-summary__lower-container">
            {this.renderServiceBadges()}
          </div>
        </div>
        {this.renderTimeline()}
      </div>
    );
  }
}

TraceSummary.propTypes = propTypes;

export default TraceSummary;
