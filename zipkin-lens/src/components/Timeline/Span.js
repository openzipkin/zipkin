import PropTypes from 'prop-types';
import React from 'react';

import SpanInfo from './SpanInfo';
import { getErrorTypeColor, getServiceNameColor } from '../../util/color';

const propTypes = {
  startTs: PropTypes.number.isRequired,
  endTs: PropTypes.number.isRequired,
  traceDuration: PropTypes.number.isRequired,
  traceTimestamp: PropTypes.number.isRequired,
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

  calculateLeftAndWidth(baseLeft, baseWidth) {
    const {
      startTs,
      endTs,
      traceDuration,
    } = this.props;

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
              <div
                className="timeline__span-service-name-column-button"
                style={{ left: `${(span.depth - 1) * 14}px` }}
                onClick={this.handleChildrenToggle}
                role="presentation"
              >
                {
                  isChildrenOpened
                    ? (<span className="fas fa-minus-square" />)
                    : (<span className="fas fa-plus-square" />)
                }
              </div>
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

  renderTimeMarkers() {
    const { numTimeMarkers } = this.props;

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

    return timeMarkers;
  }

  renderSpanDuration(left, width) {
    const { span } = this.props;

    if (parseInt(left, 10) > 50) {
      return (
        <span
          className="timeline__span-duration timeline__span-duration--right"
          style={{ right: `${100 - (left + width)}%` }}
        >
          {span.durationStr}
        </span>
      );
    }

    return (
      <span
        className="timeline__span-duration timeline__span-duration--left"
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
        clientFinish.timestamp, clientStart.timestamp,
      );
      const serverBaseWidth = this.calculateBaseWidth(
        serverFinish.timestamp, serverStart.timestamp,
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
        <div className="timeline__span-bar-wrapper">
          <span
            className="timeline__span-bar timeline__span-bar--client"
            style={{
              left: `${clientLeft}%`,
              width: `${clientWidth}%`,
            }}
          />
          <span
            className="timeline__span-bar timeline__span-bar--server"
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
      <div className="timeline__span-bar-wrapper">
        <span
          className="timeline__span-bar"
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

  renderTimeMarkersAndBar() {
    return (
      <div>
        {this.renderTimeMarkers()}
        {this.renderSpanBar()}
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
            <div>
              {this.renderTimeMarkers()}
              {this.renderSpanBar()}
            </div>
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
