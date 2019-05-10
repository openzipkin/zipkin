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

import TraceSummary from './TraceSummary';
import { sortTraceSummaries } from './sorting';
import { traceSummariesPropTypes } from '../../prop-types';

const propTypes = {
  traceSummaries: traceSummariesPropTypes.isRequired,
  tracesMap: PropTypes.shape({}).isRequired,
  sortingMethod: PropTypes.string.isRequired,
};

const BrowserResults = ({ traceSummaries, sortingMethod, tracesMap }) => (
  <div className="browser-results">
    {
      sortTraceSummaries(traceSummaries, sortingMethod).map(
        traceSummary => (
          <div
            key={traceSummary.traceId}
            className="browser-results__trace-summary-wrapper"
            data-test="trace-summary-wrapper"
          >
            <TraceSummary
              traceSummary={traceSummary}
              skewCorrectedTrace={tracesMap[traceSummary.traceId]}
            />
          </div>
        ),
      )
    }
  </div>
);

BrowserResults.propTypes = propTypes;

export default BrowserResults;
