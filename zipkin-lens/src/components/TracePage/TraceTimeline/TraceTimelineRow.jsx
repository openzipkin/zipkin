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
import classnames from 'classnames';
import { withStyles } from '@material-ui/styles';

import {
  spanDataRowPosY,
  spanBarLinePosY,
  spanOffsetY,
  spanBarRowPosY,
  serviceNamePosXPercent,
  spanNamePosXPercent,
  durationPosXPercent,
  timelineOffsetXPercent,
  spanBarHeight,
  spanHeight,
  spanBarWidthPercent,
  spanBarPosXPercent,
} from '../sizing';
import { detailedSpanPropTypes } from '../../../prop-types';
import { selectServiceColor } from '../../../colors';

const propTypes = {
  span: detailedSpanPropTypes.isRequired,
  index: PropTypes.number.isRequired,
  onRowClick: PropTypes.func.isRequired,
  isFocused: PropTypes.bool.isRequired,
  startTs: PropTypes.number.isRequired,
  endTs: PropTypes.number.isRequired,
  classes: PropTypes.shape({}).isRequired,
};

const style = theme => ({
  serviceName: {
    textTransform: 'uppercase',
  },
  button: {
    opacity: 0,
    fill: theme.palette.grey[500],
    cursor: 'pointer',
    '&:hover': {
      opacity: 0.2,
    },
  },
  'button--focused': {
    opacity: 0.3,
  },
  barLine: {
    stroke: theme.palette.grey[500],
    strokeWidth: '1px',
  },
});

const TraceTimelineRow = ({
  span,
  index,
  onRowClick,
  isFocused,
  startTs,
  endTs,
  classes,
}) => {
  const duration = endTs - startTs;
  const left = (span.timestamp - startTs) / duration * 100;
  // Span's duration sometimes is not set, so give a default value
  // to display a bar on the UI.
  // This value is not valid at all.
  let width = 1;
  if (span.duration) {
    width = Math.max(span.duration / duration * 100, 1);
  }

  return (
    <g>
      <text x={`${serviceNamePosXPercent}%`} y={spanDataRowPosY(index)} className={classes.serviceName}>
        {span.serviceName}
      </text>
      <text x={`${spanNamePosXPercent}%`} y={spanDataRowPosY(index)}>
        {span.spanName}
      </text>
      <text x={`${durationPosXPercent}%`} y={spanDataRowPosY(index)}>
        {span.durationStr}
      </text>
      <line
        x1={`${timelineOffsetXPercent}%`}
        x2="100%"
        y1={spanBarLinePosY(index)}
        y2={spanBarLinePosY(index)}
        className={classes.barLine}
      />
      <rect
        width={`${spanBarWidthPercent(width)}%`}
        height={spanBarHeight}
        x={`${spanBarPosXPercent(left)}%`}
        y={spanBarRowPosY(index)}
        rx={2}
        ry={2}
        fill={selectServiceColor(span.serviceName)}
      />
      <rect
        className={classnames(classes.button, { [classes['button--focused']]: isFocused })}
        x={0}
        y={spanOffsetY(index)}
        width="100%"
        height={spanHeight}
        onClick={() => onRowClick(span.spanId)}
      />
    </g>
  );
};

TraceTimelineRow.propTypes = propTypes;

export default withStyles(style)(TraceTimelineRow);
