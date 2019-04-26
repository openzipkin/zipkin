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
import React from 'react';
import moment from 'moment';

import TraceSummaryBar from './TraceSummaryBar';
import { traceSummaryPropTypes } from '../../../prop-types';

const propTypes = {
  traceSummary: traceSummaryPropTypes.isRequired,
};

const TraceSummaryUpperBar = ({ traceSummary }) => (
  <TraceSummaryBar
    width={traceSummary.width}
    infoClass={traceSummary.infoClass}
  >
    <div className="trace-summary-upper-bar__label">
      <div className="trace-summary-upper-bar__data">
        <div className="trace-summary-upper-bar__duration" data-test="duration">
          {`${traceSummary.durationStr}`}
        </div>
        &nbsp;-&nbsp;
        <div className="trace-summary-upper-bar__spans" data-test="spans">
          {`${traceSummary.spanCount} spans`}
        </div>
      </div>
      <div data-test="timestamp">
        {moment(traceSummary.timestamp / 1000).format('MM/DD HH:mm:ss:SSS')}
      </div>
    </div>
  </TraceSummaryBar>
);

TraceSummaryUpperBar.propTypes = propTypes;

export default TraceSummaryUpperBar;
