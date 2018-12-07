/*
 * Copyright 2018 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

import PropTypes from 'prop-types';
import React from 'react';

const propTypes = {
  traceSummary: PropTypes.shape({}).isRequired,
};

const TraceInfo = ({ traceSummary }) => (
  <div className="trace__trace-info">
    {
      [
        { label: 'Duration', value: traceSummary.durationStr },
        { label: 'Services', value: traceSummary.services },
        { label: 'Depth', value: traceSummary.depth },
        { label: 'Total Spans', value: traceSummary.totalSpans },
      ].map(elem => (
        <div
          key={elem.label}
          className="trace__trace-info-info"
        >
          <div className="trace__trace-info-label">
            {elem.label}
          </div>
          <div className="trace__trace-info-value">
            {elem.value}
          </div>
        </div>
      ))
    }
  </div>
);


TraceInfo.propTypes = propTypes;

export default TraceInfo;
