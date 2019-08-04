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

import TraceTimelineRow from './TraceTimelineRow';
import { detailedTraceSummaryPropTypes } from '../../../prop-types';
import { spanDataRowLineHeight, spanBarRowLineHeight, spanTreeWidthPercent } from './constants';

const propTypes = {
  traceSummary: detailedTraceSummaryPropTypes.isRequired,
  width: PropTypes.number.isRequired,
};

const TraceTimeline = ({ traceSummary, width }) => {
  const spanCounts = traceSummary.spans.length;
  const traceTimelineHeight = (spanDataRowLineHeight + spanBarRowLineHeight) * spanCounts;
  const traceTimelineOffsetX = width * (spanTreeWidthPercent / 100);
  const traceTimelineWidth = width * ((100 - spanTreeWidthPercent) / 100);

  return (
    <svg
      version="1.1"
      width={width}
      height={`${traceTimelineHeight}rem`}
      xmlns="http://www.w3.org/2000/svg"
    >
      {
        traceSummary.spans.map((span, i) => (
          <TraceTimelineRow
            key={span.spanId}
            span={span}
            index={i}
            offsetX={traceTimelineOffsetX}
            width={traceTimelineWidth}
          />
        ))
      }
    </svg>
  );
};

TraceTimeline.propTypes = propTypes;

export default TraceTimeline;
