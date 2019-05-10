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

import TimelineHeader from './TimelineHeader';
import TimelineSpan from './TimelineSpan';
import { detailedTraceSummaryPropTypes } from '../../prop-types';

const propTypes = {
  startTs: PropTypes.number.isRequired,
  endTs: PropTypes.number.isRequired,
  traceSummary: detailedTraceSummaryPropTypes.isRequired,
};

const defaultServiceNameColumnWidth = 0.2;
const defaultSpanNameColumnWidth = 0.1;
const defaultNumTimeMarkers = 5;

class Timeline extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      serviceNameColumnWidth: defaultServiceNameColumnWidth,
      spanNameColumnWidth: defaultSpanNameColumnWidth,
      childrenClosedSpans: {},
      dataOpenedSpans: {},
    };
    this.handleServiceNameColumnWidthChange = this.handleServiceNameColumnWidthChange.bind(this);
    this.handleSpanNameColumnWidthChange = this.handleSpanNameColumnWidthChange.bind(this);
    this.handleChildrenOpenToggle = this.handleChildrenOpenToggle.bind(this);
    this.handleDataOpenToggle = this.handleDataOpenToggle.bind(this);
  }

  handleServiceNameColumnWidthChange(serviceNameColumnWidth) {
    this.setState({ serviceNameColumnWidth });
  }

  handleSpanNameColumnWidthChange(spanNameColumnWidth) {
    this.setState({ spanNameColumnWidth });
  }

  handleChildrenOpenToggle(spanId) {
    const { childrenClosedSpans: prevChildrenClosedSpans } = this.state;

    let childrenClosedSpans = {};
    if (prevChildrenClosedSpans[spanId]) {
      childrenClosedSpans = {
        ...prevChildrenClosedSpans,
        [spanId]: undefined,
      };
    } else {
      childrenClosedSpans = {
        ...prevChildrenClosedSpans,
        [spanId]: true,
      };
    }
    this.setState({ childrenClosedSpans });
  }

  handleDataOpenToggle(spanId) {
    const { dataOpenedSpans: prevDataOpenedSpans } = this.state;

    let dataOpenedSpans = {};
    if (prevDataOpenedSpans[spanId]) {
      dataOpenedSpans = {
        ...prevDataOpenedSpans,
        [spanId]: false,
      };
    } else {
      dataOpenedSpans = {
        ...prevDataOpenedSpans,
        [spanId]: true,
      };
    }
    this.setState({ dataOpenedSpans });
  }

  render() {
    const { startTs, endTs, traceSummary } = this.props;
    const {
      serviceNameColumnWidth,
      spanNameColumnWidth,
      childrenClosedSpans,
      dataOpenedSpans,
    } = this.state;

    const closed = {};
    for (let i = 0; i < traceSummary.spans.length; i += 1) {
      if (childrenClosedSpans[traceSummary.spans[i].parentId]) {
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
          numTimeMarkers={defaultNumTimeMarkers}
          onServiceNameColumnWidthChange={this.handleServiceNameColumnWidthChange}
          onSpanNameColumnWidthChange={this.handleSpanNameColumnWidthChange}
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
                <TimelineSpan
                  key={span.spanId}
                  startTs={startTs}
                  endTs={endTs}
                  traceDuration={traceSummary.duration}
                  traceTimestamp={traceSummary.spans[0].timestamp}
                  numTimeMarkers={defaultNumTimeMarkers}
                  serviceNameColumnWidth={serviceNameColumnWidth}
                  spanNameColumnWidth={spanNameColumnWidth}
                  span={span}
                  hasChildren={hasChildren}
                  areChildrenOpened={!childrenClosedSpans[span.spanId]}
                  areDataOpened={!!dataOpenedSpans[span.spanId]}
                  onChildrenOpenToggle={this.handleChildrenOpenToggle}
                  onDataOpenToggle={this.handleDataOpenToggle}
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
