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
import { connect } from 'react-redux';

import { loadTrace } from '../../actions/trace-action';
import TracePage from '../../components/TracePage';

const mapStateToProps = (state, ownProps) => ({
  traceId: ownProps.match.params.traceId,
  isLoading: state.trace.isLoading,
  traceSummary: state.trace.traceSummary,
  correctedTraceMap: state.traces.correctedTraceMap,
});

const mapDispatchToProps = dispatch => ({
  loadTrace: (traceId, correctedTraceMap) => dispatch(loadTrace(traceId, correctedTraceMap)),
});

const TracePageContainer = connect(
  mapStateToProps,
  mapDispatchToProps,
  // 3rd argument is called mergeProps.
  // Please see https://github.com/reduxjs/react-redux/blob/master/docs/api/connect.md#mergeprops-stateprops-dispatchprops-ownprops--object
  (stateProps, dispatchProps, ownProps) => ({
    ...ownProps,
    ...stateProps,
    loadTrace: (traceId) => {
      const { correctedTraceMap } = stateProps;
      dispatchProps.loadTrace(traceId, correctedTraceMap);
    },
  }),
)(TracePage);

export default TracePageContainer;
