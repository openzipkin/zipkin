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
import { withRouter } from 'react-router';
import {
  ResponsiveContainer,
  ScatterChart,
  CartesianGrid,
  Scatter,
  XAxis,
  YAxis,
  Tooltip,
} from 'recharts';
import moment from 'moment';

import { theme } from '../../../colors';
import { formatDuration } from '../../../util/timestamp';
import { traceSummariesPropTypes } from '../../../prop-types';

const propTypes = {
  traceSummaries: traceSummariesPropTypes.isRequired,
  history: PropTypes.shape({ push: PropTypes.func.isRequired }).isRequired,
};

const xAxisFormatter = timestamp => moment(timestamp).format('MM/DD hh:mm:ss');
const yAxisFormatter = duration => formatDuration(duration);

const tooltipFormatter = (value, name) => {
  if (name === 'Start Time') {
    return xAxisFormatter(value);
  }
  return yAxisFormatter(value);
};

const TracesScatter = ({ traceSummaries, history }) => {
  const handleScatterClick = useCallback(({ traceId }) => {
    history.push(`/zipkin/traces/${traceId}`);
  }, [history]);

  return (
    <ResponsiveContainer width="100%" height={200}>
      <ScatterChart margin={{ top: 30, right: 50, bottom: 20 }}>
        <CartesianGrid strokeDasharray="5 3" />
        <Tooltip cursor={{ strokeDasharray: '3 3' }} formatter={tooltipFormatter} />
        <XAxis name="Start Time" type="number" dataKey="timestamp" domain={['auto', 'auto']} tickFormatter={xAxisFormatter} />
        <YAxis name="Duration" dataKey="duration" domain={['auto', 'auto']} tickFormatter={yAxisFormatter} />
        <Scatter name="Start Time" data={traceSummaries} fill={theme.palette.primary.main} onClick={handleScatterClick} />
      </ScatterChart>
    </ResponsiveContainer>
  );
};

TracesScatter.propTypes = propTypes;

export default withRouter(TracesScatter);
