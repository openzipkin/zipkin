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
import { makeStyles } from '@material-ui/styles';

import { formatDuration } from '../../../util/timestamp';

const propTypes = {
  numTimeMarkers: PropTypes.number.isRequired,
  duration: PropTypes.number.isRequired,
};

const useStyles = makeStyles({
  marker: {
    width: '1px',
    height: '70px',
    backgroundColor: '#999',
    position: 'absolute',
  },
  label: {
    fontSize: '0.8rem',
    left: '2px',
    position: 'absolute',
  },
  lastLabel: {
    fontSize: '0.8rem',
    left: 'initial',
    right: '2px',
    position: 'absolute',
  },
});

const TraceMiniMapTimeMarker = ({ numTimeMarkers, duration }) => {
  const classes = useStyles();

  const timeMarkers = [];

  for (let i = 0; i < numTimeMarkers; i += 1) {
    const label = (i / (numTimeMarkers - 1)) * duration;
    const portion = i / (numTimeMarkers - 1);

    timeMarkers.push(
      <Box
        key={portion}
        position="absolute"
        className={
          portion < 1 && portion > 0 ? classes.marker : ''
        }
        style={{
          left: `${portion * 100}%`,
        }}
      >
        <Box
          component="span"
          position="absolute"
          className={portion < 1 ? classes.label : classes.lastLabel}
        >
          {formatDuration(label)}
        </Box>
      </Box>,
    );
  }
  return (
    <Box
      position="relative"
      height={15}
      overflow="visible"
    >
      {timeMarkers}
    </Box>
  );
};

TraceMiniMapTimeMarker.propTypes = propTypes;

export default TraceMiniMapTimeMarker;
