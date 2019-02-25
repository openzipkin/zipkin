import PropTypes from 'prop-types';
import React from 'react';

import TimelineHeader from './TimelineHeader';
import Span from './Span';
import { detailedTraceSummaryPropTypes } from '../../prop-types';

const propTypes = {
  startTs: PropTypes.number.isRequired,
  endTs: PropTypes.number.isRequired,
  traceSummary: detailedTraceSummaryPropTypes.isRequired,
};

const DEFAULT_SERVICE_NAME_COLUMN_WIDTH = 0.20;
const DEFAULT_SPAN_NAME_COLUMN_WIDTH = 0.1;
const DEFAULT_NUM_TICKS = 5;

class Timeline extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      serviceNameColumnWidth: DEFAULT_SERVICE_NAME_COLUMN_WIDTH,
      spanNameColumnWidth: DEFAULT_SPAN_NAME_COLUMN_WIDTH,
      childrenClosed: {},
      infoOpened: {},
    };
    this.handleServiceNameColumnChange = this.handleServiceNameColumnChange.bind(this);
    this.handleSpanNameColumnChange = this.handleSpanNameColumnChange.bind(this);
    this.handleChildrenToggle = this.handleChildrenToggle.bind(this);
    this.handleInfoToggle = this.handleInfoToggle.bind(this);
  }

  handleServiceNameColumnChange(value) {
    this.setState({ serviceNameColumnWidth: value });
  }

  handleSpanNameColumnChange(value) {
    this.setState({ spanNameColumnWidth: value });
  }

  handleChildrenToggle(spanId) {
    const { childrenClosed } = this.state;

    if (childrenClosed[spanId]) {
      childrenClosed[spanId] = undefined;
    } else {
      childrenClosed[spanId] = true;
    }
    this.setState({ childrenClosed });
  }

  handleInfoToggle(spanId) {
    const { infoOpened } = this.state;

    if (infoOpened[spanId]) {
      infoOpened[spanId] = false;
    } else {
      infoOpened[spanId] = true;
    }
    this.setState({ infoOpened });
  }

  isChildrenOpened(spanId) {
    const {
      childrenClosed,
    } = this.state;
    if (childrenClosed[spanId]) {
      return false;
    }
    return true;
  }

  isInfoOpened(spanId) {
    const {
      infoOpened,
    } = this.state;
    if (infoOpened[spanId]) {
      return true;
    }
    return false;
  }

  render() {
    const {
      startTs,
      endTs,
      traceSummary,
    } = this.props;

    const {
      serviceNameColumnWidth,
      spanNameColumnWidth,
      childrenClosed,
    } = this.state;

    const closed = {};
    for (let i = 0; i < traceSummary.spans.length; i += 1) {
      if (childrenClosed[traceSummary.spans[i].parentId]) {
        closed[traceSummary.spans[i].spanId] = true;
      }
    }

    return (
      <div className="timeline">
        <TimelineHeader
          startTs={startTs}
          endTs={endTs}
          serviceNameColumnWidth={serviceNameColumnWidth}
          spanNameColumnWidth={spanNameColumnWidth}
          numTimeMarkers={DEFAULT_NUM_TICKS}
          onServiceNameColumnWidthChange={this.handleServiceNameColumnChange}
          onSpanNameColumnWidthChange={this.handleSpanNameColumnChange}
        />
        {
          traceSummary.spans.map(
            (span, index, spans) => {
              let hasChildren = false;
              if (index < spans.length - 1) {
                if (spans[index + 1].depth > span.depth) {
                  hasChildren = true;
                }
              }
              /* Skip closed spans */
              if (closed[span.spanId]) {
                if (hasChildren) {
                  for (let i = 0; i < span.childIds.length; i += 1) {
                    closed[span.childIds[i]] = true;
                  }
                }
                return null;
              }
              return (
                <Span
                  key={span.spanId}
                  startTs={startTs}
                  endTs={endTs}
                  traceDuration={traceSummary.duration}
                  traceTimestamp={traceSummary.spans[0].timestamp}
                  numTimeMarkers={DEFAULT_NUM_TICKS}
                  serviceNameColumnWidth={serviceNameColumnWidth}
                  spanNameColumnWidth={spanNameColumnWidth}
                  span={span}
                  hasChildren={hasChildren}
                  isChildrenOpened={this.isChildrenOpened(span.spanId)}
                  isInfoOpened={this.isInfoOpened(span.spanId)}
                  onChildrenToggle={this.handleChildrenToggle}
                  onInfoToggle={this.handleInfoToggle}
                />
              );
            },
          )
        }
      </div>
    );
  }
}

Timeline.propTypes = propTypes;

export default Timeline;
