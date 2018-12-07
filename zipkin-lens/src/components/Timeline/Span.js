import PropTypes from 'prop-types';
import React from 'react';

import SpanInfo from './SpanInfo';
import Button from '../Common/Button';
import { getErrorTypeColor, getServiceNameColor } from '../../util/color';

const propTypes = {
  startTs: PropTypes.number.isRequired,
  endTs: PropTypes.number.isRequired,
  traceDuration: PropTypes.number.isRequired,
  numTimeMarkers: PropTypes.number.isRequired,
  serviceNameColumnWidth: PropTypes.number.isRequired,
  spanNameColumnWidth: PropTypes.number.isRequired,
  span: PropTypes.shape({}).isRequired,
  hasChildren: PropTypes.bool.isRequired,
  isChildrenOpened: PropTypes.bool.isRequired,
  isInfoOpened: PropTypes.bool.isRequired,
  onChildrenToggle: PropTypes.func.isRequired,
  onInfoToggle: PropTypes.func.isRequired,
};

class Span extends React.Component {
  constructor(props) {
    super(props);

    this.handleChildrenToggle = this.handleChildrenToggle.bind(this);
    this.handleInfoToggle = this.handleInfoToggle.bind(this);
  }

  handleChildrenToggle(e) {
    e.stopPropagation(); /* Stop event bubbling */
    const { span, onChildrenToggle } = this.props;
    onChildrenToggle(span.spanId);
  }

  handleInfoToggle() {
    const { span, onInfoToggle } = this.props;
    onInfoToggle(span.spanId);
  }

  renderServiceNameColumn() {
    const { span, hasChildren, isChildrenOpened } = this.props;

    return (
      <div className="timeline__span-service-name-column">
        {
          hasChildren
            ? (
              <Button
                className="timeline__span-service-name-column-button"
                style={{ left: `${(span.depth - 1) * 14}px` }}
                onClick={this.handleChildrenToggle}
              >
                { isChildrenOpened ? '+' : '-' }
              </Button>
            )
            : null
        }
        <span
          className="timeline__span-service-name-column-depth-marker"
          style={{
            left: `${span.depth * 14}px`,
            background: `${getServiceNameColor(span.serviceName)}`,
          }}
        />
        <div
          className="timeline__span-service-name-column-name-wrapper"
          style={{ left: `${(span.depth + 1) * 14}px` }}
        >
          <div className="timeline__span-service-name-column-name">
            {span.serviceName}
          </div>
        </div>
      </div>
    );
  }

  renderTimeMarkersAndBar() {
    const {
      startTs,
      endTs,
      span,
      numTimeMarkers,
      traceDuration,
    } = this.props;

    const timeMarkers = [];
    for (let i = 1; i < numTimeMarkers - 1; i += 1) {
      const portion = i / (numTimeMarkers - 1);
      timeMarkers.push(
        <span
          key={portion}
          className="timeline__time-marker"
          style={{ left: `${portion * 100}%` }}
        />,
      );
    }

    const spanStartTs = span.left * traceDuration / 100;
    const spanEndTs = spanStartTs + (span.width * traceDuration / 100);
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

    return (
      <div>
        {timeMarkers}
        <div className="timeline__span-bar-wrapper">
          <span
            className="timeline__span-bar"
            style={{
              left: `${left}%`,
              width: `${width}%`,
              background: `${getErrorTypeColor(span.errorType)}`,
            }}
          >
            <span
              className={`timeline__span-duration ${parseInt(left, 10) > 50
                ? 'right' : 'left'}`}
            >
              {span.durationStr}
            </span>
          </span>
        </div>
      </div>
    );
  }

  render() {
    const {
      span,
      serviceNameColumnWidth,
      spanNameColumnWidth,
      isInfoOpened,
    } = this.props;
    return (
      <div>
        <div
          role="presentation"
          className="timeline__span"
          onClick={this.handleInfoToggle}
        >
          <div
            className="timeline__span-service-name-column-wrapper"
            style={{ width: `${serviceNameColumnWidth * 100}%` }}
          >
            { this.renderServiceNameColumn() }
          </div>
          <div
            className="timeline__span-span-name-column-wrapper"
            style={{ width: `${spanNameColumnWidth * 100}%` }}
          >
            <div className="timeline__span-span-name-column">
              { span.spanName }
            </div>
          </div>
          <div
            className="timeline__span-time-markers-bar"
            style={{
              width: `${(1 - (serviceNameColumnWidth + spanNameColumnWidth)) * 100}%`,
            }}
          >
            { this.renderTimeMarkersAndBar() }
          </div>
        </div>
        {
          isInfoOpened
            ? (
              <SpanInfo
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

Span.propTypes = propTypes;

export default Span;
