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

import useServiceBadge from './ServiceBadge.hook';
import { selectServiceColor } from '../../../colors';

const propTypes = {
  xPercent: PropTypes.number.isRequired,
  yPixel: PropTypes.number.isRequired,
  widthPercent: PropTypes.number.isRequired,
  heightPixel: PropTypes.number.isRequired,
  serviceName: PropTypes.string.isRequired,
};

const ServiceBadge = ({
  xPercent,
  yPixel,
  widthPercent,
  heightPixel,
  serviceName,
}) => {
  const [rectEl, textEl] = useServiceBadge(serviceName);

  return (
    <svg
      x={`${xPercent}%`}
      y={`${yPixel}px`}
      width={`${widthPercent}%`}
      height={`${heightPixel}px`}
    >
      <rect
        ref={rectEl}
        rx={3}
        ry={3}
        x={0}
        y={0}
        width="100%"
        height="100%"
        fill={selectServiceColor(serviceName)}
      />
      <text
        ref={textEl}
        x="50%"
        y="50%"
        fill="#fff"
        textAnchor="middle"
        dominantBaseline="central"
      />
    </svg>
  );
};

ServiceBadge.propTypes = propTypes;

export default ServiceBadge;
