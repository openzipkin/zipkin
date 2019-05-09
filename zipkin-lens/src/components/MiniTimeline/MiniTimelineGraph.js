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

import MiniTimelineTimeMarkers from './MiniTimelineTimeMarkers';
import { getGraphHeight, getGraphLineHeight } from './util';
import { getServiceNameColor } from '../../util/color';
import { detailedSpansPropTypes } from '../../prop-types';

const leftMouseButton = 0;

const propTypes = {
  spans: detailedSpansPropTypes.isRequired,
  startTs: PropTypes.number.isRequired,
  endTs: PropTypes.number.isRequired,
  duration: PropTypes.number.isRequired,
  onStartAndEndTsChange: PropTypes.func.isRequired,
  numTimeMarkers: PropTypes.number.isRequired,
};

class MiniTimelineGraph extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      isDragging: false,
      dragStartX: null,
      dragCurrentX: null,
    };
    this.element = undefined;
    this.handleMouseDown = this.handleMouseDown.bind(this);
    this.handleMouseMove = this.handleMouseMove.bind(this);
    this.handleMouseUp = this.handleMouseUp.bind(this);
  }

  getPositionX(clientX) {
    const { left, width } = this.element.getBoundingClientRect();
    return (clientX - left) / width;
  }

  handleMouseDown(event) {
    if (event.button !== leftMouseButton) {
      return;
    }
    const currentX = this.getPositionX(event.clientX);
    this.setState({
      isDragging: true,
      dragStartX: currentX,
      dragCurrentX: currentX,
    });
    window.addEventListener('mousemove', this.handleMouseMove);
    window.addEventListener('mouseup', this.handleMouseUp);
  }

  handleMouseMove(event) {
    this.setState({ dragCurrentX: this.getPositionX(event.clientX) });
  }

  handleMouseUp(event) {
    const { duration, onStartAndEndTsChange } = this.props;
    const { dragStartX } = this.state;
    this.setState({ isDragging: false });

    let startTs;
    let endTs;
    const currentX = this.getPositionX(event.clientX);
    if (currentX > dragStartX) {
      startTs = Math.max(dragStartX * duration, 0);
      endTs = Math.min(currentX * duration, duration);
    } else {
      startTs = Math.max(currentX * duration, 0);
      endTs = Math.min(dragStartX * duration, duration);
    }
    onStartAndEndTsChange(startTs, endTs);

    window.removeEventListener('mousemove', this.handleMouseMove);
    window.removeEventListener('mouseup', this.handleMouseUp);
  }

  render() {
    const {
      spans, startTs, endTs, duration, numTimeMarkers,
    } = this.props;
    const { isDragging, dragStartX, dragCurrentX } = this.state;
    const graphHeight = getGraphHeight(spans.length);
    const graphLineHeight = getGraphLineHeight(spans.length);
    return (
      <div
        className="mini-timeline-graph"
        style={{ height: graphHeight }}
        ref={(element) => { this.element = element; }}
        role="presentation"
        onMouseDown={this.handleMouseDown}
      >
        <svg version="1.1" width="100%" height={graphHeight} xmlns="http://www.w3.org/2000/svg">
          <MiniTimelineTimeMarkers
            height={graphHeight}
            numTimeMarkers={numTimeMarkers}
          />
          {
            spans.map((span, i) => (
              <rect
                key={span.spanId}
                width={`${span.width}%`}
                height={graphLineHeight}
                x={`${span.left}%`}
                y={i * graphLineHeight}
                fill={getServiceNameColor(span.serviceName)}
              />
            ))
          }
          {
            isDragging
              ? (
                <g stroke="#999" strokeWidth="1">
                  <line
                    x1={`${dragStartX * 100}%`}
                    x2={`${dragStartX * 100}%`}
                    y1={0}
                    y2={graphHeight}
                  />
                  <line
                    x1={`${dragStartX * 100}%`}
                    x2={`${dragCurrentX * 100}%`}
                    y1={graphHeight / 2}
                    y2={graphHeight / 2}

                  />
                  <line
                    x1={`${dragCurrentX * 100}%`}
                    x2={`${dragCurrentX * 100}%`}
                    y1={0}
                    y2={graphHeight}
                  />
                </g>
              )
              : null
          }
          {
            startTs
              ? (
                <rect
                  width={`${startTs / duration * 100}%`}
                  height={graphHeight}
                  x="0"
                  y="0"
                  fill="rgba(50, 50, 50, 0.2)"
                />
              )
              : null
          }
          {
            endTs
              ? (
                <rect
                  width={`${(duration - endTs) / duration * 100}%`}
                  height={graphHeight}
                  x={`${endTs / duration * 100}%`}
                  y="0"
                  fill="rgba(50, 50, 50, 0.2)"
                />
              )
              : null
          }
        </svg>
      </div>
    );
  }
}

MiniTimelineGraph.propTypes = propTypes;

export default MiniTimelineGraph;
