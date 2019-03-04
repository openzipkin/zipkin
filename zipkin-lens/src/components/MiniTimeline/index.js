import PropTypes from 'prop-types';
import React from 'react';

import { formatDuration } from '../../util/timestamp';
import { getServiceNameColor } from '../../util/color';

const propTypes = {
  startTs: PropTypes.number.isRequired,
  endTs: PropTypes.number.isRequired,
  traceSummary: PropTypes.shape({}).isRequired,
  onStartAndEndTsChange: PropTypes.func.isRequired,
};

const graphHeight = 75;
const numTimeMarkers = 5;
const leftMouseButton = 0;

const renderTimeMarkers = () => {
  const timeMarkers = [];
  for (let i = 1; i < numTimeMarkers - 1; i += 1) {
    const portion = 100 / (numTimeMarkers - 1) * i;
    timeMarkers.push(
      <line
        key={portion}
        x1={`${portion}%`}
        x2={`${portion}%`}
        y1="0"
        y2={graphHeight}
      />,
    );
  }
  return (
    <g
      stroke="#888"
      strokeWidth="1"
    >
      {timeMarkers}
    </g>
  );
};

class MiniTimeline extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      isDragging: false,
      dragStartX: null,
      dragCurrentX: null,
    };
    this._graphElement = undefined;

    this.setGraphElement = this.setGraphElement.bind(this);
    this.handleMouseDown = this.handleMouseDown.bind(this);
    this.handleMouseMove = this.handleMouseMove.bind(this);
    this.handleMouseUp = this.handleMouseUp.bind(this);
    this.handleDoubleClick = this.handleDoubleClick.bind(this);
  }

  setGraphElement(element) {
    this._graphElement = element;
  }

  getPosition(clientX) {
    const { left, width } = this._graphElement.getBoundingClientRect();
    return (clientX - left) / width;
  }

  handleMouseDown(event) {
    if (event.button !== leftMouseButton) {
      return;
    }
    const currentX = this.getPosition(event.clientX);
    this.setState({
      isDragging: true,
      dragStartX: currentX,
      dragCurrentX: currentX,
    });
    window.addEventListener('mousemove', this.handleMouseMove);
    window.addEventListener('mouseup', this.handleMouseUp);
  }

  handleMouseMove(event) {
    this.setState({
      dragCurrentX: this.getPosition(event.clientX),
    });
  }

  handleMouseUp(event) {
    const { traceSummary, onStartAndEndTsChange } = this.props;
    const { dragStartX } = this.state;
    this.setState({ isDragging: false });

    let startTs;
    let endTs;
    const currentX = this.getPosition(event.clientX);
    if (currentX > dragStartX) {
      startTs = Math.max(dragStartX * traceSummary.duration, 0);
      endTs = Math.min(currentX * traceSummary.duration, traceSummary.duration);
    } else {
      startTs = Math.max(currentX * traceSummary.duration, 0);
      endTs = Math.min(dragStartX * traceSummary.duration, traceSummary.duration);
    }
    onStartAndEndTsChange(startTs, endTs);

    window.removeEventListener('mousemove', this.handleMouseMove);
    window.removeEventListener('mouseup', this.handleMouseUp);
  }

  handleDoubleClick() {
    const { traceSummary, onStartAndEndTsChange } = this.props;
    onStartAndEndTsChange(0, traceSummary.duration);
  }

  renderTicks() {
    const { traceSummary } = this.props;

    const timeMarkers = [];
    for (let i = 0; i < numTimeMarkers; i += 1) {
      const label = formatDuration((i / (numTimeMarkers - 1)) * (traceSummary.duration));

      const portion = i / (numTimeMarkers - 1);

      let portionClassName = '';
      if (portion === 0) {
        portionClassName = 'first';
      } else if (portion >= 1) {
        portionClassName = 'last';
      }

      timeMarkers.push(
        <div
          key={portion}
          className="mini-trace-viewer__time-marker"
          style={{
            left: `${portion * 100}%`,
          }}
        >
          <span className={`mini-trace-viewer__time-marker-label ${portionClassName}`}>
            {label}
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
    const { traceSummary, startTs, endTs } = this.props;
    const { isDragging, dragStartX, dragCurrentX } = this.state;
    const { spans } = traceSummary;
    const lineHeight = graphHeight / spans.length;

    return (
      <div className="mini-trace-viewer">
        <div className="mini-trace-viewer__time-markers-wrapper">
          {this.renderTicks()}
        </div>
        <div
          className="mini-trace-viewer__graph"
          ref={this.setGraphElement}
          role="presentation"
          onMouseDown={this.handleMouseDown}
          onDoubleClick={this.handleDoubleClick}
        >
          <svg version="1.1" width="100%" height={graphHeight} xmlns="http://www.w3.org/2000/svg">
            {renderTimeMarkers()}
            {
              spans.map((span, i) => (
                <rect
                  key={span.spanId}
                  width={`${span.width}%`}
                  height={lineHeight}
                  x={`${span.left}%`}
                  y={i * lineHeight}
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
                    width={`${startTs / traceSummary.duration * 100}%`}
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
                    width={`${(traceSummary.duration - endTs) / traceSummary.duration * 100}%`}
                    height={graphHeight}
                    x={`${endTs / traceSummary.duration * 100}%`}
                    y="0"
                    fill="rgba(50, 50, 50, 0.2)"
                  />
                )
                : null
            }
          </svg>
        </div>
      </div>
    );
  }
}

MiniTimeline.propTypes = propTypes;

export default MiniTimeline;
