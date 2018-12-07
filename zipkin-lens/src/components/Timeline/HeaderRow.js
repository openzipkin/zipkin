/*
 * Copyright 2018 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
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

const LEFT_MOUSE_BUTTON = 0;
const MIN_COLUMN_WIDTH = 0.075;

class HeaderRow extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      widthChangingColumn: null,
    };

    this._element = undefined;

    this.setElement = this.setElement.bind(this);
    this.handleMouseDown = this.handleMouseDown.bind(this);
    this.handleMouseMove = this.handleMouseMove.bind(this);
    this.handleMouseUp = this.handleMouseUp.bind(this);
  }

  setElement(element) {
    this._element = element;
  }

  getPosition(clientX) {
    const { left, width } = this._element.getBoundingClientRect();
    return (clientX - left) / width;
  }

  handleMouseDown(event, column) {
    if (event.button !== LEFT_MOUSE_BUTTON) {
      return;
    }
    this.setState({
      widthChangingColumn: column,
    });
    window.addEventListener('mousemove', this.handleMouseMove);
    window.addEventListener('mouseup', this.handleMouseUp);
  }

  handleMouseMove(event) {
    const {
      serviceNameColumnWidth,
      onServiceNameColumnWidthChange,
      onSpanNameColumnWidthChange,
    } = this.props;

    const {
      widthChangingColumn,
    } = this.state;

    switch (widthChangingColumn) {
      case 'service':
        onServiceNameColumnWidthChange(
          Math.max(this.getPosition(event.clientX), MIN_COLUMN_WIDTH),
        );
        break;
      case 'span':
        onSpanNameColumnWidthChange(
          Math.max(this.getPosition(event.clientX) - serviceNameColumnWidth, MIN_COLUMN_WIDTH),
        );
        break;
      default:
        break;
    }
  }

  handleMouseUp() {
    window.removeEventListener('mousemove', this.handleMouseMove);
    window.removeEventListener('mouseup', this.handleMouseUp);
  }

  renderTicks() {
    const {
      startTs,
      endTs,
      numTimeMarkers,
    } = this.props;

    const timeMarkers = [];
    for (let i = 0; i < numTimeMarkers; i += 1) {
      const label = startTs + (i / (numTimeMarkers - 1)) * (endTs - startTs);
      const portion = i / (numTimeMarkers - 1);

      let positionClassName = '';
      if (portion <= 0) {
        positionClassName = 'first';
      } else if (portion >= 1) {
        positionClassName = 'last';
      }

      timeMarkers.push(
        <div
          key={portion}
          className={`timeline__time-marker ${positionClassName}`}
          style={{ left: `${portion * 100}%` }}
        >
          <span className={`timeline__header-row-time-marker-label ${positionClassName}`}>
            {formatDuration(label)}
          </span>
        </div>,
      );
    }
    return (
      <div>
        {timeMarkers}
      </div>
    );
  }

  render() {
    const {
      serviceNameColumnWidth,
      spanNameColumnWidth,
    } = this.props;
    return (
      <div className="timeline__header-row" ref={this.setElement}>
        <div
          className="timeline__header-row-title-splitter-wrapper"
          style={{ width: `${serviceNameColumnWidth * 100}%` }}
        >
          <div className="timeline__header-row-title">
            Service Name
          </div>
          <div
            className="timeline__header-row-splitter"
            role="presentation"
            onMouseDown={
              (e) => { this.handleMouseDown(e, 'service'); }
            }
          >
            ||
          </div>
        </div>
        <div
          className="timeline__header-row-title-splitter-wrapper"
          style={{ width: `${spanNameColumnWidth * 100}%` }}
        >
          <div className="timeline__header-row-title">
            Span Name
          </div>
          <div
            className="timeline__header-row-splitter"
            role="presentation"
            onMouseDown={
              (e) => { this.handleMouseDown(e, 'span'); }
            }
          >
            ||
          </div>
        </div>
        <div
          className="timeline__header-row-time-markers"
          style={{
            width: `${(1 - (serviceNameColumnWidth + spanNameColumnWidth)) * 100}%`,
          }}
        >
          { this.renderTicks() }
        </div>
      </div>
    );
  }
}

HeaderRow.propTypes = propTypes;

export default HeaderRow;
