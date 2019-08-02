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
// import { AutoSizer } from 'react-virtualized';

import TraceSummaryHeader from './TraceSummaryHeader';
import MiniTimeline from '../MiniTimeline';
import Timeline from '../Timeline';
import { detailedTraceSummaryPropTypes } from '../../prop-types';

const propTypes = {
  traceSummary: detailedTraceSummaryPropTypes.isRequired,
};

const TraceSummary = ({ traceSummary }) => {
  const [tsRange, setTsRange] = useState({
    startTs: 0,
    endTs: traceSummary.duration,
  });

  const handleStartAndEndTsChange = useCallback((startTs, endTs) => {
    setTsRange({
      startTs,
      endTs,
    });
  }, []);

  return (
    <React.Fragment>
      <TraceSummaryHeader traceSummary={traceSummary} />
      <MiniTimeline
        startTs={tsRange.startTs}
        endTs={tsRange.endTs}
        traceSummary={traceSummary}
        onStartAndEndTsChange={handleStartAndEndTsChange}
      />
      <Box height="100%" mb={3}>
        {
          /*
            <AutoSizer>
              {
                ({ height, width }) => (
                  <Box
                    height={height}
                    width={width}
                    overflow="auto"
                  >
                    <Timeline
                      startTs={tsRange.startTs}
                      endTs={tsRange.endTs}
                      traceSummary={traceSummary}
                    />
                  </Box>
                )
              }
            </AutoSizer>
          */
        }
        <Timeline
          startTs={tsRange.startTs}
          endTs={tsRange.endTs}
          traceSummary={traceSummary}
        />
      </Box>
    </React.Fragment>
  );
};

TraceSummary.propTypes = propTypes;

export default TraceSummary;
