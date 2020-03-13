/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
import TimeMarker from './TimeMarker';

const propTypes = {
  currentSpanId: PropTypes.string.isRequired,
  spans: detailedSpansPropTypes.isRequired,
  depth: PropTypes.number.isRequired,
  childrenHiddenSpanIds: PropTypes.shape({}).isRequired,
  isRootedTrace: PropTypes.bool.isRequired,
  onRowClick: PropTypes.func.isRequired,
  onChildrenToggle: PropTypes.func.isRequired,
  startTs: PropTypes.number.isRequired,
  endTs: PropTypes.number.isRequired,
};

const TraceTimeline = React.memo(
  ({
    currentSpanId,
    spans,
    depth,
    childrenHiddenSpanIds,
    isRootedTrace,
    onRowClick,
    onChildrenToggle,
    startTs,
    endTs,
  }) => (
    <svg
      version="1.1"
      xmlns="http://www.w3.org/2000/svg"
      width="100%"
      height={`${timelineHeight(spans.length)}px`}
    >
      <TimeMarker />
      {spans.map((span, idx) => (
        <TraceTimelineRow
          key={span.spanId}
          span={span}
          onRowClick={onRowClick}
          index={idx}
          isFocused={currentSpanId === span.spanId}
          startTs={startTs}
          endTs={endTs}
        />
      ))}
      {isRootedTrace ? (
        <TraceTree
          spans={spans}
          depth={depth}
          childrenHiddenSpanIds={childrenHiddenSpanIds}
          onChildrenToggle={onChildrenToggle}
        />
      ) : null}
    </svg>
  ),
);

TraceTimeline.propTypes = propTypes;

export default TraceTimeline;
