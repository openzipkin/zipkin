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
import React from 'react';

import { selectServiceColor } from '../../../colors';
import { detailedSpansPropTypes } from '../../../prop-types';

const propTypes = {
  spans: detailedSpansPropTypes.isRequired,
};

const graphHeight = 55; // px

const TraceMiniMapGraph = ({ spans }) => (
  <svg
    version="1.1"
    width="100%"
    height={graphHeight}
    xmlns="http://www.w3.org/2000/svg"
  >
    {
      spans.map((span, i) => (
        <rect
          key={span.spanId}
          width={`${Math.max(span.width, 1)}%`}
          height={Math.max(graphHeight / spans.length, 5)}
          x={`${span.left}%`}
          y={i * graphHeight / spans.length}
          rx={1}
          ry={1}
          fill={selectServiceColor(span.serviceName)}
        />
      ))
    }
  </svg>
);

TraceMiniMapGraph.propTypes = propTypes;

export default TraceMiniMapGraph;
