import PropTypes from 'prop-types';
import React from 'react';

const propTypes = {
  height: PropTypes.number.isRequired,
  numTimeMarkers: PropTypes.number.isRequired,
};

const MiniTimelineTimeMarkers = ({ height, numTimeMarkers }) => {
  const timeMarkers = [];
  for (let i = 1; i < numTimeMarkers - 1; i += 1) {
    const portion = 100 / (numTimeMarkers - 1) * i;
    timeMarkers.push(
      <line
        key={portion}
        x1={`${portion}%`}
        x2={`${portion}%`}
        y1="0"
        y2={height}
      />,
    );
  }
  return (
    <g stroke="#888" strokeWidth="1">
      {timeMarkers}
    </g>
  );
};

MiniTimelineTimeMarkers.propTypes = propTypes;

export default MiniTimelineTimeMarkers;
