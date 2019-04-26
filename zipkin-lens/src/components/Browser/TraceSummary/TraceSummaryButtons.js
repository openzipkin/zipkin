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
import { Link } from 'react-router-dom';

import * as api from '../../../constants/api';

const propTypes = {
  traceId: PropTypes.string.isRequired,
};

const TraceSummaryButtons = ({ traceId }) => (
  <div className="trace-summary-buttons">
    <a href={`${api.TRACE}/${traceId}`} target="_brank" data-test="download">
      <i className="fas fa-file-download" />
    </a>
    <Link to={{ pathname: `/zipkin/traces/${traceId}` }} data-test="trace">
      <i className="fas fa-angle-double-right" />
    </Link>
  </div>
);

TraceSummaryButtons.propTypes = propTypes;

export default TraceSummaryButtons;
