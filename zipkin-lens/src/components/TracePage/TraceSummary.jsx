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
import React, { useState, useCallback } from 'react';
import Box from '@material-ui/core/Box';
import { AutoSizer } from 'react-virtualized';

import TraceSummaryHeader from './TraceSummaryHeader';
import TraceTimeline from './TraceTimeline';
import SpanDetail from './SpanDetail';
import { detailedTraceSummaryPropTypes } from '../../prop-types';

const propTypes = {
  traceSummary: detailedTraceSummaryPropTypes.isRequired,
};

const TraceSummary = ({ traceSummary }) => {
  const [currentSpanIndex, setCurrentSpanIndex] = useState(0);

  const handleSpanClick = useCallback(i => setCurrentSpanIndex(i), []);

  const currentSpan = traceSummary.spans[currentSpanIndex];

  return (
    <React.Fragment>
      <Box boxShadow={3} zIndex={1}>
        <TraceSummaryHeader traceSummary={traceSummary} />
      </Box>
      <Box height="100%" display="flex">
        <Box height="100%" width="65%">
          <AutoSizer>
            {
              ({ height, width }) => (
                <Box
                  height={height}
                  width={width}
                  overflow="auto"
                >
                  <TraceTimeline
                    traceSummary={traceSummary}
                    width={width}
                    onSpanClick={handleSpanClick}
                  />
                </Box>
              )
            }
          </AutoSizer>
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
                  <SpanDetail span={currentSpan} />
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
