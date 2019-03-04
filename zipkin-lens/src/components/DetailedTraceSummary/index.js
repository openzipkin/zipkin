import React from 'react';

import Timeline from '../Timeline';
import MiniTimeline from '../MiniTimeline';
import { detailedTraceSummaryPropTypes } from '../../prop-types';

const propTypes = {
  traceSummary: detailedTraceSummaryPropTypes.isRequired,
};

class DetailedTraceSummary extends React.Component {
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
    const { startTs, endTs } = this.state;
    const { traceSummary } = this.props;

    return (
      <div className="detailed-trace-summary">
        {this.renderHeader()}
        <div className="detailed-trace-summary__mini-trace-viewer-wrapper">
          <MiniTimeline
            startTs={startTs || 0}
            endTs={endTs || traceSummary.duration}
            traceSummary={traceSummary}
            onStartAndEndTsChange={this.handleStartAndEndTsChange}
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

export default DetailedTraceSummary;
