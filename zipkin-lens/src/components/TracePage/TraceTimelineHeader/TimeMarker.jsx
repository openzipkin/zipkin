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
import { makeStyles } from '@material-ui/styles';
import Box from '@material-ui/core/Box';
import classNames from 'classnames';

import {
  spanTreeWidthPercent,
  timelineWidthPercent,
  serviceNameWidthPercent,
} from '../sizing';
import { formatDuration } from '../../../util/timestamp';

const propTypes = {
  startTs: PropTypes.number.isRequired,
  endTs: PropTypes.number.isRequired,
};

const useStyles = makeStyles((theme) => ({
  marker: {
    height: '100%',
    width: '1px',
    backgroundColor: theme.palette.grey[500],
    position: 'absolute',
  },
  label: {
    fontSize: '0.8rem',
    left: '4px',
    position: 'absolute',
  },
  'label--last': {
    left: 'initial',
    right: '4px',
  },
}));

const numTimeMarkers = 4;

const TimeMarker = React.memo(({ startTs, endTs }) => {
  const classes = useStyles();
  const timeMarkers = [];

  for (let i = 0; i < numTimeMarkers; i += 1) {
    const label = startTs + (i / (numTimeMarkers - 1)) * (endTs - startTs);
    const portion = i / (numTimeMarkers - 1);

    timeMarkers.push(
      <Box
        key={portion}
        position="absolute"
        className={classes.marker}
        style={{ left: `${portion * 100}%` }}
        data-testid="TimeMarker-marker"
      >
        <Box
          component="span"
          position="absolute"
          className={classNames(classes.label, {
            [classes['label--last']]: portion >= 1,
          })}
          data-testid="TimeMarker-label"
        >
          {formatDuration(label)}
        </Box>
      </Box>,
    );
  }
  return (
    <Box display="flex">
      <Box width={`${spanTreeWidthPercent + serviceNameWidthPercent}%`} />
      <Box
        mt={1}
        height={15}
        position="relative"
        width={`${timelineWidthPercent}%`}
      >
        {timeMarkers}
      </Box>
    </Box>
  );
});

TimeMarker.propTypes = propTypes;

export default TimeMarker;
