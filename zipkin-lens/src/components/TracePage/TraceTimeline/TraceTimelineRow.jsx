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
  spanBarOffsetY,
  spanBarHeight,
  spanBarWidthPercent,
  spanBarOffsetXPercent,
} from '../sizing';
import { detailedSpanPropTypes } from '../../../prop-types';

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
  bar: {
    opacity: 0.8,
    fill: theme.palette.primary.main,
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
  let left;
  let width;
  if (span.duration) {
    width = Math.max(span.duration / duration * 100, 1);
    left = (span.timestamp - startTs) / duration * 100;
  } else {
    // If duration is 0 (in other words, if startTs and endTs of the list of spans to be
    // displayed match), it can be considered that this span is the only span displayed
    // on the trace timeline graph.
    // In that case, width should be 100% and left should be 0%.
    const isSingleSpanTrace = duration === 0;
    if (isSingleSpanTrace) {
      width = 100;
      left = 0;
    } else {
      // Give the span a default value to display the span bar in the UI even if the
      // span does not have duration.
      width = 1;
      left = (span.timestamp - startTs) / duration * 100;
    }
  }

  return (
    <g>
      <rect
        width={`${spanBarWidthPercent(width)}%`}
        height={spanBarHeight - 4}
        x={`${spanBarOffsetXPercent(left)}%`}
        y={spanBarOffsetY(index) + 2}
        rx={4}
        ry={4}
        className={classes.bar}
      />
      <rect
        className={classnames(classes.button, { [classes['button--focused']]: isFocused })}
        x={0}
        y={spanBarOffsetY(index)}
        width="100%"
        height={spanBarHeight}
        onClick={() => onRowClick(span.spanId)}
      />
      <text
        x={`${spanBarOffsetXPercent(left) + 1}%`}
        y={spanBarOffsetY(index) + 4 + spanBarHeight / 2}
        style={{
          fontSize: '1.05rem',
          fontWeight: 'bold',
        }}
      >
        {`${span.spanName}: ${span.durationStr}`}
      </text>
    </g>
  );
};

TraceTimelineRow.propTypes = propTypes;

export default withStyles(style)(TraceTimelineRow);
