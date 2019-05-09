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
