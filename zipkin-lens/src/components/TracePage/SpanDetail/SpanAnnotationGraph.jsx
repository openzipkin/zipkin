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
import PropTypes from 'prop-types';
import Box from '@material-ui/core/Box';
import { makeStyles } from '@material-ui/styles';
import classnames from 'classnames';
import minBy from 'lodash/minBy';
import maxBy from 'lodash/maxBy';

import { selectServiceColor } from '../../../constants/color';
import { spanAnnotationsPropTypes } from '../../../prop-types';
import { generateAnnotationKey } from './util';

//
// Size & Position:
//
//        ____________________________________           _
// ------|              SPAN BAR              |-----     | spanBarHeight
//        ------------------------------------           -
//
// |-----------------------------------------------|
//                     width = 100%
// |-----|                                    |----|
//   leftOffsetPercent                      rightOffsetPercent
//
//   Spanbar's width and left and right edge coordinates are as follows
//
//       |------------------------------------|
//                    spanBarWidth = 100 - (leftOffsetPercent + rightOffsetPercent) (%)
//       |
//       x = leftOffsetPercent (%)
//                                            |
//                                          x = 100 - rightOffsetPercent (%)
//
// ============================================================================
//
// Annotation Position:
//
//        ____________________________________           _
// ------|         @    SPAN BAR              |-----     | spanBarHeight
//        ------------------------------------           -
//
//       |---------|--------------------------|
//           A%                 100-A%
//                 |
//                 x = spanBarWidth * (A / 100) + leftOffsetPercent (%)
//
//
// ============================================================================
//
// Example:
//
//        ____________________________________           _
// ------|         @    SPAN BAR              |-----     | spanBarHeight
//        ------------------------------------           -
//
// |-----------------------------------------------|
//                       width = 100% = 500px
// |-----|                                    |----|
//    5%                                         5%
//       |---------|--------------------------|
//           30%                70%
//
//                 |
//               x = spanBarWidth * (A / 100) + leftOffsetPercent
//                 = (100 - (5 + 5)) * (30 / 100) + 5
//                 = 32%
//                 = 500 * 0.32 = 160px
//

const leftOffsetPercent = 5;
const rightOffsetPercent = 5;
const spanBarHeight = '16'; // px

const propTypes = {
  serviceName: PropTypes.string.isRequired,
  annotations: spanAnnotationsPropTypes.isRequired,
  onAnnotationCircleClick: PropTypes.func.isRequired,
  currentAnnotationKey: PropTypes.string,
};

const defaultProps = {
  currentAnnotationKey: '',
};

const useStyles = makeStyles((theme) => ({
  svg: {
    stroke: theme.palette.grey[500],
    strokeWidth: '1px',
  },
  circle: {
    cursor: 'pointer',
    fill: theme.palette.common.white,
    '&:hover': {
      fill: theme.palette.grey[400],
    },
  },
  'circle--selected': {
    fill: theme.palette.grey[400],
    strokeWidth: '3px',
  },
}));

const SpanAnnotationGraph = React.memo(
  ({
    serviceName,
    annotations,
    onAnnotationCircleClick,
    currentAnnotationKey,
  }) => {
    const classes = useStyles();

    let minTs;
    let maxTs;
    if (annotations.length === 0) {
      minTs = 0;
      maxTs = 0;
    } else if (annotations.length === 1) {
      minTs = annotations[0].timestamp;
      maxTs = minTs + 1;
    } else {
      minTs = minBy(annotations, 'timestamp').timestamp;
      maxTs = maxBy(annotations, 'timestamp').timestamp;
    }

    const duration = maxTs - minTs;

    return (
      <Box height="50px" width="100%" display="flex" alignItems="center">
        <svg width="100%" height={spanBarHeight} className={classes.svg}>
          <line x1="0%" x2="100%" y1="8px" y2="8px" />
          <rect
            x={`${leftOffsetPercent}%`}
            y="0"
            width={`${100 - (leftOffsetPercent + rightOffsetPercent)}%`}
            height={spanBarHeight}
            rx={2}
            ry={2}
            fill={selectServiceColor(serviceName)}
          />
          {annotations.map((annotation) => {
            const annotationKey = generateAnnotationKey(annotation);
            const cx =
              (100 - (leftOffsetPercent + rightOffsetPercent)) *
                ((annotation.timestamp - minTs) / duration) +
              leftOffsetPercent;
            return (
              <circle
                key={annotationKey}
                cx={`${cx}%`}
                cy="8px"
                r="6px"
                className={classnames(classes.circle, {
                  [classes['circle--selected']]:
                    annotationKey === currentAnnotationKey,
                })}
                onClick={() => onAnnotationCircleClick(annotation)}
                data-testid="span-annotation-graph--circle"
              />
            );
          })}
        </svg>
      </Box>
    );
  },
);

SpanAnnotationGraph.propTypes = propTypes;
SpanAnnotationGraph.defaultProps = defaultProps;

export default SpanAnnotationGraph;
