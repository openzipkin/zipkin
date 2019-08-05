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
import React, { useCallback } from 'react';
import { makeStyles } from '@material-ui/styles';
import grey from '@material-ui/core/colors/grey';

import { spanDataRowLineHeight, spanBarRowLineHeight, spanBarHeight } from './constants';
import { detailedSpanPropTypes } from '../../../prop-types';
import { selectServiceColor } from '../../../colors';

const propTypes = {
  span: detailedSpanPropTypes.isRequired,
  index: PropTypes.number.isRequired,
  offsetX: PropTypes.number.isRequired,
  width: PropTypes.number.isRequired,
  onSpanClick: PropTypes.func.isRequired,
};

const useStyles = makeStyles({
  serviceName: {
    textTransform: 'uppercase',
  },
  clickableRect: {
    opacity: 0,
    fill: grey[500],
    cursor: 'pointer',
    '&:hover': {
      opacity: 0.2,
    },
  },
});

const TraceTimelineRow = ({
  span,
  index,
  offsetX,
  width,
  onSpanClick,
}) => {
  const classes = useStyles();

  const spanRowOffsetY = index * (spanDataRowLineHeight + spanBarRowLineHeight);
  const spanDataRowPosY = spanRowOffsetY + spanDataRowLineHeight * 0.75;
  const spanBarRowPosY = spanRowOffsetY + spanDataRowLineHeight;

  const handleClick = useCallback(() => {
    onSpanClick(index);
  }, [onSpanClick, index]);

  return (
    <g>
      <text
        x={offsetX + width * 0.05}
        y={`${spanDataRowPosY}rem`}
        className={classes.serviceName}
      >
        {span.serviceName}
      </text>
      <text
        x={offsetX + width * 0.3}
        y={`${spanDataRowPosY}rem`}
      >
        {span.spanName}
      </text>
      <rect
        width={width * (span.width / 100)}
        height={`${spanBarHeight}rem`}
        x={offsetX + (width * (span.left / 100))}
        y={`${spanBarRowPosY}rem`}
        rx={2}
        ry={2}
        fill={selectServiceColor(span.serviceName)}
      />
      <rect
        className={classes.clickableRect}
        x={0}
        y={`${spanRowOffsetY}rem`}
        width="100%"
        height={`${spanDataRowLineHeight + spanBarRowLineHeight}rem`}
        onClick={handleClick}
      />
    </g>
  );
};

TraceTimelineRow.propTypes = propTypes;

export default TraceTimelineRow;
