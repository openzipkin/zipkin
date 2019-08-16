/*
 * Copyright 2015-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
import PropTypes from 'prop-types';
import React from 'react';

import TraceTree from './TraceTree';
import TraceTimelineRow from './TraceTimelineRow';
import { detailedSpansPropTypes } from '../../../prop-types';
import { timelineHeight } from '../sizing';

const propTypes = {
  spans: detailedSpansPropTypes.isRequired,
  depth: PropTypes.number.isRequired,
  closedSpans: PropTypes.shape({}).isRequired,
  onSpanClick: PropTypes.func.isRequired,
  onSpanToggleButtonClick: PropTypes.func.isRequired,
};

const TraceTimeline = ({
  spans,
  depth,
  closedSpans,
  onSpanClick,
  onSpanToggleButtonClick,
}) => (
  <svg
    version="1.1"
    xmlns="http://www.w3.org/2000/svg"
    width="100%"
    height={`${timelineHeight(spans.length)}px`}
  >
    {
      spans.map((span, i) => (
        <TraceTimelineRow
          key={span.spanId}
          span={span}
          index={i}
          onSpanClick={onSpanClick}
        />
      ))
    }
    <TraceTree
      spans={spans}
      depth={depth}
      closedSpans={closedSpans}
      onSpanToggleButtonClick={onSpanToggleButtonClick}
    />
  </svg>
);

TraceTimeline.propTypes = propTypes;

export default TraceTimeline;