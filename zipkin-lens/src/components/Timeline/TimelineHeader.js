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

import { formatDuration } from '../../util/timestamp';

const propTypes = {
  startTs: PropTypes.number.isRequired,
  endTs: PropTypes.number.isRequired,
  serviceNameColumnWidth: PropTypes.number.isRequired,
  spanNameColumnWidth: PropTypes.number.isRequired,
  numTimeMarkers: PropTypes.number.isRequired,
  onSpanNameColumnWidthChange: PropTypes.func.isRequired,
  onServiceNameColumnWidthChange: PropTypes.func.isRequired,
};

const leftMouseButton = 0;
const minServiceNameColumnWidth = 0.1;
const minSpanNameColumnWidth = 0.075;
const serviceNameColumn = 'SERVICE_NAME_COLUMN';
const spanNameColumn = 'SPAN_NAME_COLUMN';

class TimelineHeader extends React.Component {
  constructor(props) {
    super(props);
    this.state = { resizingColumnName: null };
    this.element = undefined;
    this.handleMouseDown = this.handleMouseDown.bind(this);
    this.handleMouseMove = this.handleMouseMove.bind(this);
    this.handleMouseUp = this.handleMouseUp.bind(this);
  }

  getPosition(clientX) {
    const { left, width } = this.element.getBoundingClientRect();
    return (clientX - left) / width;
  }

  handleMouseDown(event, columnName) {
    if (event.button !== leftMouseButton) {
      return;
    }
    this.setState({ resizingColumnName: columnName });
    window.addEventListener('mousemove', this.handleMouseMove);
    window.addEventListener('mouseup', this.handleMouseUp);
  }

  handleMouseMove(event) {
    const {
      serviceNameColumnWidth,
      onServiceNameColumnWidthChange,
      onSpanNameColumnWidthChange,
    } = this.props;
    const { resizingColumnName } = this.state;

    if (resizingColumnName === serviceNameColumn) {
      onServiceNameColumnWidthChange(
        Math.max(this.getPosition(event.clientX), minServiceNameColumnWidth),
      );
    } else if (resizingColumnName === spanNameColumn) {
      onSpanNameColumnWidthChange(
        Math.max(
          this.getPosition(event.clientX) - serviceNameColumnWidth, minSpanNameColumnWidth,
        ),
      );
    }
  }

  handleMouseUp() {
    window.removeEventListener('mousemove', this.handleMouseMove);
    window.removeEventListener('mouseup', this.handleMouseUp);
  }

  renderResizableColumn(columnName) {
    const { serviceNameColumnWidth, spanNameColumnWidth } = this.props;
    let label = '';
    let columnWidth = 0;
    if (columnName === serviceNameColumn) {
      label = 'Service Name';
      columnWidth = serviceNameColumnWidth;
    } else if (columnName === spanNameColumn) {
      label = 'Span Name';
      columnWidth = spanNameColumnWidth;
    }

    return (
      <div
        className="timeline-header__resizable-column"
        style={{ width: `${columnWidth * 100}%` }}
      >
        <div className="timeline-header__column-name">
          {label}
        </div>
        <div
          className="timeline-header__draggable-splitter"
          role="presentation"
          onMouseDown={(e) => { this.handleMouseDown(e, columnName); }}
        >
          ||
        </div>
      </div>
    );
  }

  renderTimeMarkers() {
    const {
      startTs,
      endTs,
      numTimeMarkers,
      serviceNameColumnWidth,
      spanNameColumnWidth,
    } = this.props;

    const timeMarkers = [];
    for (let i = 0; i < numTimeMarkers; i += 1) {
      const label = startTs + (i / (numTimeMarkers - 1)) * (endTs - startTs);
      const portion = i / (numTimeMarkers - 1);

      let modifier = '';
      if (portion <= 0) {
        modifier = '--first';
      } else if (portion >= 1) {
        modifier = '--last';
      }

      timeMarkers.push(
        <div
          key={portion}
          className={`timeline-header__time-marker timeline-header__time-marker${modifier}`}
          style={{ left: `${portion * 100}%` }}
        >
          <span className={`timeline-header__time-marker-label timeline-header__time-marker-label${modifier}`}>
            {formatDuration(label)}
          </span>
        </div>,
      );
    }
    return (
      <div
        className="timeline-header__time-markers"
        style={{
          width: `${(1 - (serviceNameColumnWidth + spanNameColumnWidth)) * 100}%`,
        }}
      >
        {timeMarkers}
      </div>
    );
  }

  render() {
    return (
      <div className="timeline-header" ref={(element) => { this.element = element; }}>
        {this.renderResizableColumn(serviceNameColumn)}
        {this.renderResizableColumn(spanNameColumn)}
        {this.renderTimeMarkers()}
      </div>
    );
  }
}

TimelineHeader.propTypes = propTypes;

export default TimelineHeader;
