/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import PropTypes from 'prop-types';
import React from 'react';

import { formatDuration } from '../../util/timestamp';

const propTypes = {
  numTimeMarkers: PropTypes.number.isRequired,
  duration: PropTypes.number.isRequired,
};

const MiniTimelineLabel = ({ numTimeMarkers, duration }) => {
  const timeMarkers = [];
  for (let i = 0; i < numTimeMarkers; i += 1) {
    const label = formatDuration((i / (numTimeMarkers - 1)) * duration);
    const portion = i / (numTimeMarkers - 1);

    let modifier = '';
    if (portion === 0) {
      modifier = '--first';
    } else if (portion >= 1) {
      modifier = '--last';
    }

    timeMarkers.push(
      <div
        key={portion}
        className="mini-timeline-label__label-wrapper"
        style={{ left: `${portion * 100}%` }}
        data-test="label-wrapper"
      >
        <span
          className={`mini-timeline-label__label mini-timeline-label__label${modifier}`}
          data-test="label"
        >
          {label}
        </span>
      </div>,
    );
  }
  return (
    <div className="mini-timeline-label">
      {timeMarkers}
    </div>
  );
};

MiniTimelineLabel.propTypes = propTypes;

export default MiniTimelineLabel;
