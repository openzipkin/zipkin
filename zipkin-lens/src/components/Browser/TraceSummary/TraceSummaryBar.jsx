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

import { getInfoClassColor } from '../../../util/color';

const propTypes = {
  children: PropTypes.node.isRequired,
  width: PropTypes.number.isRequired,
  infoClass: PropTypes.string.isRequired,
};

const TraceSummaryBar = ({ children, width, infoClass }) => (
  <div className="trace-summary-bar">
    <div
      className="trace-summary-bar__bar-wrapper"
      style={{ width: `${width}%` }}
      data-test="bar-wrapper"
    >
      <div
        className="trace-summary-bar__bar"
        style={{ backgroundColor: getInfoClassColor(infoClass) }}
        data-test="bar"
      />
    </div>
    <div className="trace-summary-bar__label-wrapper">
      {children}
    </div>
  </div>
);

TraceSummaryBar.propTypes = propTypes;

export default TraceSummaryBar;
