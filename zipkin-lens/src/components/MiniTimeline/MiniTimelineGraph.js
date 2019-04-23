import PropTypes from 'prop-types';
import React from 'react';

import MiniTimelineMarkers from './MiniTimelineMarkers';
import { getGraphHeight, getGraphLineHeight } from './util';
import { getServiceNameColor } from '../../util/color';
import { detailedSpansPropTypes } from '../../prop-types';

const propTypes = {
  spans: detailedSpansPropTypes.isRequired,
  startTs: PropTypes.number.isRequired,
  endTs: PropTypes.number.isRequired,
  duration: PropTypes.number.isRequired,
};

const MiniTimelineGraph = ({
  spans, startTs, endTs, duration,
}) => {
  const graphHeight = getGraphHeight(spans.length);
  const graphLineHeight = getGraphLineHeight(spans.length);
  return (
    <div className="mini-timeline-graph" style={{ height: graphHeight }}>
      <svg version="1.1" width="100%" height={graphHeight} xmlns="http://www.w3.org/2000/svg">
        <MiniTimelineMarkers />
        {
          spans.map((span, i) => (
            <rect
              key={span.spaId}
              width={`${span.width}%`}
              height={graphLineHeight}
              x={`${span.left}%`}
              y={i * graphLineHeight}
              fill={getServiceNameColor(span.serviceName)}
            />
          ))
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
};

MiniTimelineGraph.propTypes = propTypes;

export default MiniTimelineGraph;
