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
import { connect } from 'react-redux';
import queryString from 'query-string';

import Browser from '../../components/Browser';
import { clearTraces } from '../../actions/traces-action';
import {
  treeCorrectedForClockSkew,
  traceSummary,
  traceSummaries,
} from '../../zipkin';

const mapStateToProps = (state, ownProps) => {
  const { location } = ownProps;

  let serviceName;
  if (location.search !== '' && location.search !== '?') {
    const query = queryString.parse(location.search);
    serviceName = query.serviceName;
  }

  const { traces } = state.traces;
  const correctedTraces = traces.map(treeCorrectedForClockSkew);
  const correctedSummaries = traceSummaries(
    serviceName,
    correctedTraces.map(traceSummary),
  );

  const tracesMap = {};
  correctedTraces.forEach((trace, index) => {
    const [{ traceId }] = traces[index];
    tracesMap[traceId] = trace;
  });

  return {
    traceSummaries: correctedSummaries,
    tracesMap,
    isLoading: state.traces.isLoading,
  };
};

const mapDispatchToProps = dispatch => ({
  clearTraces: () => dispatch(clearTraces()),
});

const BrowserContainer = connect(
  mapStateToProps,
  mapDispatchToProps,
)(Browser);

export default BrowserContainer;
