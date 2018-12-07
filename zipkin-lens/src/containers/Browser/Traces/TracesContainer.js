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

import { connect } from 'react-redux';
import queryString from 'query-string';

import Trace from '../../../components/Browser/Traces';
import {
  correctForClockSkew,
  convert,
  mergeById,
  traceSummary,
  traceSummaries,
} from '../../../zipkin';

const mapStateToProps = (state, ownProps) => {
  const { location } = ownProps;
  let serviceName = '';
  if (location.search !== '' && location.search !== '?') {
    const query = queryString.parse(location.search);
    serviceName = query.serviceName;
  }

  const { traces } = state.traces;
  const clockSkewCorrectedTraces = traces.map((rawTrace) => {
    const v1Trace = rawTrace.map(convert);
    const mergedTrace = mergeById(v1Trace);
    return correctForClockSkew(mergedTrace);
  });
  const summaries = traceSummaries(serviceName, clockSkewCorrectedTraces.map(traceSummary));

  const clockSkewCorrectedTracesMap = {};
  clockSkewCorrectedTraces.forEach((trace) => {
    const [{ traceId }] = trace;
    clockSkewCorrectedTracesMap[traceId] = trace;
  });
  return {
    clockSkewCorrectedTracesMap,
    traceSummaries: summaries,
    isLoading: state.traces.isLoading,
  };
};

const TracesContainer = connect(
  mapStateToProps,
)(Trace);

export default TracesContainer;
