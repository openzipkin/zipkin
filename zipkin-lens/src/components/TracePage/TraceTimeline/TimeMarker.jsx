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
import React from 'react';
import { makeStyles } from '@material-ui/styles';

import {
  spanTreeWidthPercent,
  serviceNameWidthPercent,
  timelineWidthPercent,
} from '../sizing';

const numTimeMarkers = 4;

const useStyles = makeStyles((theme) => ({
  line: {
    stroke: theme.palette.grey[300],
    strokeWidth: '1px',
  },
}));

const TimeMarker = React.memo(() => {
  const classes = useStyles();
  const timeMarkers = [];

  for (let i = 0; i < numTimeMarkers; i += 1) {
    const portion = i / (numTimeMarkers - 1);
    const xPercent =
      spanTreeWidthPercent +
      serviceNameWidthPercent +
      timelineWidthPercent * portion;

    timeMarkers.push(
      <line
        key={portion}
        x1={`${xPercent}%`}
        x2={`${xPercent}%`}
        y1="0"
        y2="100%"
        className={classes.line}
      />,
    );
  }
  return <g>{timeMarkers}</g>;
});

export default TimeMarker;
