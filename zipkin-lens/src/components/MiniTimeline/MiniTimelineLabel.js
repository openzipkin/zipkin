import PropTypes from 'prop-types';
import React from 'react';

import { formatDuration } from '../../util/timestamp';

const propTypes = {
  numTimeMarkers: PropTypes.number.isRequired,
  duration: PropTypes.number.isRequired,
};

const MiniTimelineLabel = ({ numTimeMarkers, duration }) => {
  const timeMarkers = [];
  for (let i = 0; i < numTimeMarkers; i += 1) {
    const label = formatDuration((i / (numTimeMarkers - 1)) * duration);
    const portion = i / (numTimeMarkers - 1);

    let modifier = '';
    if (portion === 0) {
      modifier = '--first';
    } else if (portion >= 1) {
      modifier = '--last';
    }

    timeMarkers.push(
      <div
        key={portion}
        className="mini-timeline-label__label-wrapper"
        style={{ left: `${portion * 100}%` }}
      >
        <span className={`mini-timeline-label__label mini-timeline-label__label${modifier}`}>
          {label}
        </span>
      </div>,
    );
  }
  return (
    <div className="mini-timeline-label">
      {timeMarkers}
    </div>
  );
};

MiniTimelineLabel.propTypes = propTypes;

export default MiniTimelineLabel;
