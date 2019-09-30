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
import React, { useState, useCallback, useMemo } from 'react';
import Box from '@material-ui/core/Box';
import { AutoSizer } from 'react-virtualized';
import _ from 'lodash';

import TraceSummaryHeader from './TraceSummaryHeader';
import TraceTimeline from './TraceTimeline';
import TraceTimelineHeader from './TraceTimelineHeader';
import SpanDetail from './SpanDetail';
import { detailedTraceSummaryPropTypes } from '../../prop-types';
import { hasRootSpan } from '../../util/trace';

const propTypes = {
  traceSummary: detailedTraceSummaryPropTypes.isRequired,
};

const TraceSummary = ({ traceSummary }) => {
  const isRootedTrace = hasRootSpan(traceSummary.spans);
  const [rootSpanIndex, setRootSpanIndex] = useState(0);
  const isRerooted = rootSpanIndex !== 0;
  const [currentSpanIndex, setCurrentSpanIndex] = useState(0);
  const [childrenHiddenSpanIds, setChildrenHiddenSpanIds] = useState({});
  const [isSpanDetailOpened, setIsSpanDetaileOpened] = useState(true);
  const traceTimelineWidthPercent = isSpanDetailOpened ? 60 : 100;

  const handleChildrenToggle = useCallback((spanId) => {
    setChildrenHiddenSpanIds(prevChildrenHiddenSpanIds => ({
      ...prevChildrenHiddenSpanIds,
      [spanId]: prevChildrenHiddenSpanIds[spanId] ? undefined : true,
    }));
  }, []);

  const handleResetRerootButtonClick = useCallback(() => {
    setRootSpanIndex(0);
  }, []);

  const handleTimelineRowClick = useCallback((spanId) => {
    const idx = traceSummary.spans.findIndex(span => span.spanId === spanId);
    if (isRootedTrace && currentSpanIndex === idx) {
      setRootSpanIndex(idx);
    }
    setCurrentSpanIndex(idx);
  }, [currentSpanIndex, isRootedTrace, traceSummary.spans]);

  const shownSpans = useMemo(() => {
    // If the trace does not have a root span, the trace is not filtered anymore
    // and the entire trace should be displayed.
    if (!isRootedTrace) {
      return traceSummary.spans;
    }

    const rootSpan = traceSummary.spans[rootSpanIndex];

    const spans = [rootSpan];
    for (let i = rootSpanIndex + 1; i < traceSummary.spans.length; i += 1) {
      const span = traceSummary.spans[i];
      if (span.depth <= rootSpan.depth) {
        break;
      }
      spans.push(span);
    }

    const allHiddenSpanIds = {};
    spans.forEach((span) => {
      if (childrenHiddenSpanIds[span.parentId]) {
        allHiddenSpanIds[span.spanId] = true;
      }
      if (allHiddenSpanIds[span.spanId] && span.childIds) {
        span.childIds.forEach((childId) => {
          allHiddenSpanIds[childId] = true;
        });
      }
    });
    return spans.filter(span => !allHiddenSpanIds[span.spanId]);
  }, [childrenHiddenSpanIds, isRootedTrace, rootSpanIndex, traceSummary.spans]);

  // Find the minumum and maximum timestamps in the shown spans.
  const startTs = useMemo(() => _.minBy(shownSpans, 'timestamp').timestamp, [shownSpans]);
  const endTs = useMemo(() => {
    let max = 0;
    shownSpans.forEach((span) => {
      let ts;
      if (!span.duration) {
        ts = span.timestamp;
      } else {
        ts = span.timestamp + span.duration;
      }
      max = max < ts ? ts : max;
    });
    return max;
  }, [shownSpans]);

  const handleSpanDetailToggle = useCallback(() => {
    setIsSpanDetaileOpened(prev => !prev);
  }, []);

  const handleExpandButtonClick = useCallback(() => {
    const expandedSpanIds = shownSpans
      .filter(span => childrenHiddenSpanIds[span.spanId])
      .reduce((acc, cur) => {
        acc[cur.spanId] = undefined;
        return acc;
      }, {});
    setChildrenHiddenSpanIds(prevChildrenHiddenSpanIds => ({
      ...prevChildrenHiddenSpanIds,
      ...expandedSpanIds,
    }));
  }, [childrenHiddenSpanIds, shownSpans]);

  const handleCollapseButtonClick = useCallback(() => {
    const rootSpanId = shownSpans[0].spanId;
    setChildrenHiddenSpanIds(prevChildrenHiddenSpanIds => ({
      ...prevChildrenHiddenSpanIds,
      [rootSpanId]: true,
    }));
  }, [shownSpans]);

  return (
    <React.Fragment>
      <Box boxShadow={3} zIndex={1}>
        <TraceSummaryHeader traceSummary={traceSummary} />
      </Box>
      <Box height="100%" display="flex">
        <Box width={`${traceTimelineWidthPercent}%`} display="flex" flexDirection="column">
          <TraceTimelineHeader
            startTs={startTs - traceSummary.spans[0].timestamp}
            endTs={endTs - traceSummary.spans[0].timestamp}
            isRerooted={isRerooted}
            isRootedTrace={isRootedTrace}
            onResetRerootButtonClick={handleResetRerootButtonClick}
            isSpanDetailOpened={isSpanDetailOpened}
            onSpanDetailToggle={handleSpanDetailToggle}
            onCollapseButtonClick={handleCollapseButtonClick}
            onExpandButtonClick={handleExpandButtonClick}
          />
          <Box height="100%" width="100%">
            <AutoSizer>
              {
                ({ height, width }) => (
                  <Box height={height} width={width} overflow="auto">
                    <TraceTimeline
                      currentSpanId={traceSummary.spans[currentSpanIndex].spanId}
                      spans={shownSpans}
                      depth={traceSummary.depth}
                      childrenHiddenSpanIds={childrenHiddenSpanIds}
                      isRootedTrace={isRootedTrace}
                      onRowClick={handleTimelineRowClick}
                      onChildrenToggle={handleChildrenToggle}
                      startTs={startTs}
                      endTs={endTs}
                    />
                  </Box>
                )
              }
            </AutoSizer>
          </Box>
        </Box>
        <Box height="100%" width={`${100 - traceTimelineWidthPercent}%`}>
          <AutoSizer>
            {
              ({ height, width }) => (
                <Box height={height} width={width} overflow="auto">
                  <SpanDetail span={traceSummary.spans[currentSpanIndex]} minHeight={height} />
                </Box>
              )
            }
          </AutoSizer>
        </Box>
      </Box>
    </React.Fragment>
  );
};

TraceSummary.propTypes = propTypes;

export default TraceSummary;
