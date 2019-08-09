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
import Box from '@material-ui/core/Box';

import TraceMiniMapGraph from './TraceMiniMapGraph';
import { detailedSpansPropTypes } from '../../../prop-types';
import TraceMiniMapTimeMarker from './TraceMiniMapTimeMarker';

const propTypes = {
  startTs: PropTypes.number.isRequired,
  endTs: PropTypes.number.isRequired,
  spans: detailedSpansPropTypes.isRequired,
  duration: PropTypes.number.isRequired,
  onStartTsChange: PropTypes.func.isRequired,
  onEndTsChange: PropTypes.func.isRequired,
};

const numTimeMarkers = 4;

const TraceMiniMap = ({
  startTs,
  endTs,
  spans,
  duration,
  onStartTsChange,
  onEndTsChange,
}) => (
  <Box
    width="100%"
    height="70px"
    borderBottom={1}
    borderColor="grey.300"
  >
    <TraceMiniMapTimeMarker
      numTimeMarkers={numTimeMarkers}
      duration={duration}
    />
    <TraceMiniMapGraph
      spans={spans}
      duration={duration}
      startTs={startTs}
      endTs={endTs}
      onStartTsChange={onStartTsChange}
      onEndTsChange={onEndTsChange}
      numTimeMarkers={numTimeMarkers}
    />
  </Box>
);

TraceMiniMap.propTypes = propTypes;

export default TraceMiniMap;
