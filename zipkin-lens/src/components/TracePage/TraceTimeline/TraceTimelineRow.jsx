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
import { makeStyles } from '@material-ui/styles';

import { spanDataRowLineHeight, spanBarRowLineHeight, spanBarHeight } from './constants';
import { detailedSpanPropTypes } from '../../../prop-types';
import { selectServiceColor } from '../../../colors';

const propTypes = {
  span: detailedSpanPropTypes.isRequired,
  index: PropTypes.number.isRequired,
};

const useStyles = makeStyles({
  serviceName: {
    textTransform: 'uppercase',
  },
});

const TraceTimelineRow = ({ span, index }) => {
  const classes = useStyles();

  const spanRowOffsetY = index * (spanDataRowLineHeight + spanBarRowLineHeight);
  const spanDataRowPosY = spanRowOffsetY + spanDataRowLineHeight * 0.75;
  const spanBarRowPosY = spanRowOffsetY + spanDataRowLineHeight;

  return (
    <g>
      <text
        x="5%"
        y={`${spanDataRowPosY}rem`}
        className={classes.serviceName}
      >
        {span.serviceName}
      </text>
      <text x="30%" y={`${spanDataRowPosY}rem`}>
        {span.spanName}
      </text>
      <line
        x1="0"
        x2="100%"
        y1={`${spanBarRowPosY + spanBarHeight / 2}rem`}
        y2={`${spanBarRowPosY + spanBarHeight / 2}rem`}
        strokeWidth="1"
        stroke="#999"
      />
      <rect
        width={`${span.width}%`}
        height={`${spanBarHeight}rem`}
        x={`${span.left}%`}
        y={`${spanBarRowPosY}rem`}
        fill={selectServiceColor(span.serviceName)}
      />
    </g>
  );
};

TraceTimelineRow.propTypes = propTypes;

export default TraceTimelineRow;
