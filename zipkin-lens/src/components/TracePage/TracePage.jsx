/*
 * Copyright 2015-2020 The OpenZipkin Authors
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

/* eslint-disable no-shadow */

import PropTypes from 'prop-types';
import React, { useEffect } from 'react';
import { connect } from 'react-redux';
import { withRouter } from 'react-router-dom';

import MessageBar from './MessageBar';
import TraceSummary from './TraceSummary';
import TraceSummaryHeader from './TraceSummaryHeader';
import { detailedTraceSummaryPropTypes } from '../../prop-types';
import { loadTrace } from '../../slices/tracesSlice';

const propTypes = {
  traceId: PropTypes.string.isRequired,
  traceSummary: detailedTraceSummaryPropTypes,
  loadTrace: PropTypes.func.isRequired,
  isLoading: PropTypes.bool.isRequired,
};

const defaultProps = {
  traceSummary: undefined,
};

export const TracePageImpl = React.memo(
  ({ traceId, traceSummary, loadTrace, isLoading }) => {
    useEffect(() => {
      loadTrace(traceId);
    }, [traceId, loadTrace]);

    if (isLoading) {
      return null;
    }

    if (!traceSummary) {
      return (
        <>
          <TraceSummaryHeader />
          <MessageBar variant="error" message="Trace not found" />
        </>
      );
    }
    return <TraceSummary traceSummary={traceSummary} />;
  },
);

TracePageImpl.propTypes = propTypes;
TracePageImpl.defaultProps = defaultProps;

const mapStateToProps = (state, ownProps) => {
  const { match } = ownProps;
  const { traceId } = match.params;
  const props = {};
  props.traceId = traceId;
  if (state.traces.traces[traceId]) {
    props.traceSummary = state.traces.traces[traceId].adjustedTrace;
  }
  props.isLoading = state.traces.isLoading;
  return props;
};

const mapDispatchToProps = (dispatch) => ({
  loadTrace: (traceId) => dispatch(loadTrace(traceId)),
});

export default withRouter(
  connect(mapStateToProps, mapDispatchToProps)(TracePageImpl),
);
