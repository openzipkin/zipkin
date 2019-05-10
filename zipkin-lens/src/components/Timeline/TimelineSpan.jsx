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

import TimelineSpanData from './TimelineSpanData';
import { getErrorTypeColor, getServiceNameColor } from '../../util/color';
import { detailedSpanPropTypes } from '../../prop-types';

const propTypes = {
  startTs: PropTypes.number.isRequired,
  endTs: PropTypes.number.isRequired,
  traceDuration: PropTypes.number.isRequired,
  traceTimestamp: PropTypes.number.isRequired,
  numTimeMarkers: PropTypes.number.isRequired,
  serviceNameColumnWidth: PropTypes.number.isRequired,
  spanNameColumnWidth: PropTypes.number.isRequired,
  span: detailedSpanPropTypes.isRequired,
  hasChildren: PropTypes.bool.isRequired,
  areChildrenOpened: PropTypes.bool.isRequired,
  areDataOpened: PropTypes.bool.isRequired,
  onChildrenOpenToggle: PropTypes.func.isRequired,
  onDataOpenToggle: PropTypes.func.isRequired,
};

class TimelineSpan extends React.Component {
  constructor(props) {
    super(props);
    this.handleChildrenOpenToggle = this.handleChildrenOpenToggle.bind(this);
    this.handleDataOpenToggle = this.handleDataOpenToggle.bind(this);
  }

  calculateLeftAndWidth(baseLeft, baseWidth) {
    const { startTs, endTs, traceDuration } = this.props;
    const spanStartTs = baseLeft * traceDuration / 100;
    const spanEndTs = spanStartTs + (baseWidth * traceDuration / 100);
    const newDuration = endTs - startTs;

    let left;
    let width;

    if (spanStartTs < startTs && spanEndTs < startTs) {
      //  SPAN   |------------------------------|
      //  DRAG                                    |-------|
      left = 0;
      width = 0;
    } else if (spanStartTs >= endTs) {
      // SPAN              |------------------------------|
      // DRAG |----------|
      left = 100;
      width = 0;
    } else if (spanStartTs < startTs && spanEndTs > startTs && spanEndTs < endTs) {
      // SPAN |--------------------------------------|
      // DRAG                                   |---------|
      left = 0;
      width = (spanEndTs - startTs) / newDuration * 100;
    } else if (spanStartTs < startTs && spanEndTs > startTs && spanEndTs > endTs) {
      // SPAN |-------------------------------------------|
      // DRAG                 |------|
      left = 0;
      width = 100;
    } else if (spanStartTs >= startTs && spanStartTs < endTs && spanEndTs <= endTs) {
      // SPAN         |---------------------------|
      // DRAG |-------------------------------------------|
      left = (spanStartTs - startTs) / newDuration * 100;
      width = (spanEndTs - spanStartTs) / newDuration * 100;
    } else if (spanStartTs >= startTs && spanStartTs < endTs && spanEndTs > endTs) {
      // SPAN       |-------------------------------------|
      // DRAG |------------------------------------|
      left = (spanStartTs - startTs) / newDuration * 100;
      width = (endTs - spanStartTs) / newDuration * 100;
    } else {
      left = 0;
      width = 0;
    }
    return { left, width };
  }

  calculateBaseWidth(finishTs, startTs) {
    const { traceDuration } = this.props;
    return (finishTs - startTs) / traceDuration * 100;
  }

  calculateBaseLeft(startTs) {
    const { traceDuration, traceTimestamp } = this.props;
    return (startTs - traceTimestamp) / traceDuration * 100;
  }

  handleChildrenOpenToggle(e) {
    const { span, onChildrenOpenToggle } = this.props;
    onChildrenOpenToggle(span.spanId);
    e.stopPropagation(); /* Stop event bubbling */
  }

  handleDataOpenToggle() {
    const { span, onDataOpenToggle } = this.props;
    onDataOpenToggle(span.spanId);
  }

  renderServiceNameColumn() {
    const { span, hasChildren, areChildrenOpened } = this.props;

    return (
      <div className="timeline-span__service-name-column">
        {
          hasChildren
            ? (
              <div
                className="timeline-span__open-toggle-button"
                style={{ left: `${(span.depth - 1) * 14}px` }}
                onClick={this.handleChildrenOpenToggle}
                role="presentation"
              >
                {
                  areChildrenOpened
                    ? (<span className="fas fa-minus-square" />)
                    : (<span className="fas fa-plus-square" />)
                }
              </div>
            )
            : null
        }
        <span
          className="timeline-span__depth-marker"
          style={{
            left: `${span.depth * 14}px`,
            background: `${getServiceNameColor(span.serviceName)}`,
          }}
        />
        <div
          className="timeline-span__service-name-wrapper"
          style={{ left: `${(span.depth + 1) * 14}px` }}
        >
          <div className="timeline-span__service-name">
            {span.serviceName}
          </div>
        </div>
      </div>
    );
  }

  renderTimeMarkers() {
    const { numTimeMarkers } = this.props;

    const timeMarkers = [];
    for (let i = 1; i < numTimeMarkers - 1; i += 1) {
      const portion = i / (numTimeMarkers - 1);
      timeMarkers.push(
        <span
          key={portion}
          className="timeline-span__time-marker"
          style={{ left: `${portion * 100}%` }}
        />,
      );
    }
    return timeMarkers;
  }

  renderSpanDuration(left, width) {
    const { span } = this.props;

    if (parseInt(left, 10) > 50) {
      return (
        <span
          className="timeline-span__duration timeline-span__duration--right"
          style={{ right: `${100 - (left + width)}%` }}
        >
          {span.durationStr}
        </span>
      );
    }

    return (
      <span
        className="timeline-span__duration timeline-span__duration--left"
        style={{ left: `${left}%` }}
      >
        {span.durationStr}
      </span>
    );
  }

  renderSpanBar() {
    const { span } = this.props;

    const { annotations } = span;
    const clientStart = annotations.find(annotation => annotation.value === 'Client Start');
    const serverStart = annotations.find(annotation => annotation.value === 'Server Start');
    const clientFinish = annotations.find(annotation => annotation.value === 'Client Finish');
    const serverFinish = annotations.find(annotation => annotation.value === 'Server Finish');

    if (clientStart && serverStart && clientFinish && serverFinish) {
      const clientBaseWidth = this.calculateBaseWidth(
        clientFinish.timestamp,
        clientStart.timestamp,
      );
      const serverBaseWidth = this.calculateBaseWidth(
        serverFinish.timestamp,
        serverStart.timestamp,
      );
      const clientBaseLeft = this.calculateBaseLeft(clientStart.timestamp);
      const serverBaseLeft = this.calculateBaseLeft(serverStart.timestamp);

      const {
        left: clientLeft,
        width: clientWidth,
      } = this.calculateLeftAndWidth(clientBaseLeft, clientBaseWidth);

      const {
        left: serverLeft,
        width: serverWidth,
      } = this.calculateLeftAndWidth(serverBaseLeft, serverBaseWidth);

      return (
        <div className="timeline-span__bar-container">
          <span
            className="timeline-span__bar timeline-span__bar--client"
            style={{
              left: `${clientLeft}%`,
              width: `${clientWidth}%`,
            }}
          />
          <span
            className="timeline-span__bar timeline-span__bar--server"
            style={{
              left: `${serverLeft}%`,
              width: `${serverWidth}%`,
              background: `${getErrorTypeColor(span.errorType)}`,
            }}
          />
          {this.renderSpanDuration(clientLeft, clientWidth)}
        </div>
      );
    }

    const { left, width } = this.calculateLeftAndWidth(span.left, span.width);
    return (
      <div className="timeline-span__bar-container">
        <span
          className="timeline-span__bar"
          style={{
            left: `${left}%`,
            width: `${width}%`,
            background: `${getErrorTypeColor(span.errorType)}`,
          }}
        />
        {this.renderSpanDuration(left, width)}
      </div>
    );
  }

  render() {
    const {
      span,
      serviceNameColumnWidth,
      spanNameColumnWidth,
      areDataOpened,
    } = this.props;
    return (
      <div>
        <div
          role="presentation"
          className="timeline-span"
          onClick={this.handleDataOpenToggle}
        >
          <div
            className="timeline-span__service-name-column-wrapper"
            style={{ width: `${serviceNameColumnWidth * 100}%` }}
          >
            { this.renderServiceNameColumn() }
          </div>
          <div
            className="timeline-span__span-name-column-wrapper"
            style={{ width: `${spanNameColumnWidth * 100}%` }}
          >
            <div className="timeline-span__span-name-column">
              { span.spanName }
            </div>
          </div>
          <div
            className="timeline-span__bar-wrapper"
            style={{
              width: `${(1 - (serviceNameColumnWidth + spanNameColumnWidth)) * 100}%`,
            }}
          >
            {this.renderTimeMarkers()}
            {this.renderSpanBar()}
          </div>
        </div>
        {
          areDataOpened
            ? (
              <TimelineSpanData
                span={span}
                serviceNameColumnWidth={serviceNameColumnWidth}
              />
            )
            : null
        }
      </div>
    );
  }
}

TimelineSpan.propTypes = propTypes;

export default TimelineSpan;
