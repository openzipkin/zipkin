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
  const [currentSpanIndex, setCurrentSpanIndex] = useState(0);
  const [closedSpans, setClosedSpans] = useState({});
  const [rootSpanIndex, setRootSpanIndex] = useState(0);

  const isRootedTrace = hasRootSpan(traceSummary.spans);

  const handleSpanClick = useCallback((i) => {
    if (currentSpanIndex === i && isRootedTrace) {
      setRootSpanIndex(i);
    } else {
      setCurrentSpanIndex(i);
    }
  }, [currentSpanIndex, isRootedTrace]);

  const handleSpanToggleButtonClick = useCallback((spanId) => {
    setClosedSpans(oldSpans => ({
      ...oldSpans,
      [spanId]: oldSpans[spanId] ? undefined : true,
    }));
  }, []);

  const currentSpan = traceSummary.spans[currentSpanIndex];

  const filteredSpans = useMemo(() => {
    if (!isRootedTrace) {
      return traceSummary.spans;
    }

    const rootSpan = traceSummary.spans[rootSpanIndex];
    const rerootedTree = [rootSpan];
    for (let i = rootSpanIndex + 1; i < traceSummary.spans.length; i += 1) {
      const s = traceSummary.spans[i];
      if (s.depth <= rootSpan.depth) {
        break;
      }
      rerootedTree.push(s);
    }
    const hiddenSpans = {};
    rerootedTree.forEach((span) => {
      if (closedSpans[span.parentId]) {
        hiddenSpans[span.spanId] = true;
      }
    });
    return rerootedTree.filter((span, index) => {
      let hasChildren = false;
      if (
        index < rerootedTree.length - 1
        && rerootedTree[index + 1].depth > span.depth
      ) {
        hasChildren = true;
      }
      if (hiddenSpans[span.spanId]) {
        if (hasChildren) {
          span.childIds.forEach((childId) => {
            hiddenSpans[childId] = true;
          });
        }
        return false;
      }
      return true;
    });
  }, [closedSpans, traceSummary.spans, rootSpanIndex]);

  return (
    <React.Fragment>
      <Box boxShadow={3} zIndex={1}>
        <TraceSummaryHeader traceSummary={traceSummary} />
      </Box>
      <Box height="100%" display="flex">
        <Box width="65%" display="flex" flexDirection="column">
          <TraceTimelineHeader
            startTs={0}
            endTs={traceSummary.duration}
            isRootedTrace={isRootedTrace}
          />
          <Box height="100%" width="100%">
            <AutoSizer>
              {
                ({ height, width }) => (
                  <Box
                    height={height}
                    width={width}
                    overflow="auto"
                  >
                    <TraceTimeline
                      spans={filteredSpans}
                      depth={traceSummary.depth}
                      closedSpans={closedSpans}
                      isRootedTrace={isRootedTrace}
                      onSpanClick={handleSpanClick}
                      onSpanToggleButtonClick={handleSpanToggleButtonClick}
                      setRootSpanIndex={setRootSpanIndex}
                    />
                  </Box>
                )
              }
            </AutoSizer>
          </Box>
        </Box>
        <Box height="100%" width="35%">
          <AutoSizer>
            {
              ({ height, width }) => (
                <Box
                  height={height}
                  width={width}
                  overflow="auto"
                >
                  <SpanDetail span={currentSpan} minHeight={height} />
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
