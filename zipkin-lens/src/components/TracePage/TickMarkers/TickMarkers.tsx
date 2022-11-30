/*
 * Copyright 2015-2022 The OpenZipkin Authors
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

import { Box, makeStyles } from '@material-ui/core';
import React, { ReactNode, useMemo } from 'react';
import { formatDuration } from '../../../util/timestamp';

const useStyles = makeStyles((theme) => ({
  root: {
    height: 16,
    position: 'relative',
    borderRight: `1px solid ${theme.palette.divider}`,
  },
  tick: {
    height: 16,
    position: 'absolute',
    display: 'flex',
    alignItems: 'center',
    fontSize: theme.typography.caption.fontSize,
    color: theme.palette.text.secondary,
    bottom: 0,
    paddingLeft: theme.spacing(0.5),
    paddingRight: theme.spacing(0.5),
    '&:not(:last-child)': {
      borderLeft: `1px solid ${theme.palette.divider}`,
    },
  },
}));

const numOfTickMarkers = 3;

type TickMarkersProps = {
  minTimestamp: number;
  maxTimestamp: number;
};

export const TickMarkers = ({
  minTimestamp,
  maxTimestamp,
}: TickMarkersProps) => {
  const classes = useStyles();

  const ticks = useMemo(() => {
    const result: ReactNode[] = [];
    for (let i = 0; i <= numOfTickMarkers; i += 1) {
      const timestamp = formatDuration(
        ((maxTimestamp - minTimestamp) / numOfTickMarkers) * i + minTimestamp,
      );
      let left: string | undefined;
      let right: string | undefined;
      if (i === numOfTickMarkers) {
        right = '0%';
      } else {
        left = `${(i / numOfTickMarkers) * 100}%`;
      }

      result.push(
        <Box
          key={i}
          component="span"
          className={classes.tick}
          style={{ left, right }}
        >
          {timestamp}
        </Box>,
      );
    }
    return result;
  }, [classes.tick, maxTimestamp, minTimestamp]);

  return <Box className={classes.root}>{ticks}</Box>;
};
