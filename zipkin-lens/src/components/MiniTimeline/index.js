import PropTypes from 'prop-types';
import React from 'react';

import MiniTimelineGraph from './MiniTimelineGraph';
import MiniTimelineLabel from './MiniTimelineLabel';
import MiniTimelineSlider from './MiniTimelineSlider';
import { detailedTraceSummaryPropTypes } from '../../prop-types';

const defaultNumTimeMarkers = 5;

const propTypes = {
  startTs: PropTypes.number.isRequired,
  endTs: PropTypes.number.isRequired,
  traceSummary: detailedTraceSummaryPropTypes.isRequired,
  onStartAndEndTsChange: PropTypes.func.isRequired,
};

const MiniTimeline = ({
  startTs, endTs, traceSummary, onStartAndEndTsChange,
}) => {
  const { spans, duration } = traceSummary;
  if (spans.length <= 1) {
    return null;
  }

  return (
    <div className="mini-timeline">
      <MiniTimelineLabel
        numTimeMarkers={defaultNumTimeMarkers}
        duration={duration}
      />
      <MiniTimelineGraph
        spans={spans}
        duration={duration}
        startTs={startTs}
        endTs={endTs}
        onStartAndEndTsChange={onStartAndEndTsChange}
        numTimeMarkers={defaultNumTimeMarkers}
      />
      <MiniTimelineSlider
        duration={duration}
        startTs={startTs}
        endTs={endTs}
        onStartAndEndTsChange={onStartAndEndTsChange}
      />
    </div>
  );
};

MiniTimeline.propTypes = propTypes;

export default MiniTimeline;
