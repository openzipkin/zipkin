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

import PropTypes from 'prop-types';
import React, { useEffect } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { withRouter } from 'react-router-dom';

import MessageBar from './MessageBar';
import TraceSummary from './TraceSummary';
import TraceSummaryHeader from './TraceSummaryHeader';
import { LoadingIndicator } from '../common/LoadingIndicator';
import { loadTrace } from '../../slices/tracesSlice';

const propTypes = {
  match: PropTypes.shape({
    params: PropTypes.shape({
      traceId: PropTypes.string.isRequired,
    }),
  }).isRequired,
};

export const TracePageImpl = React.memo(({ match }) => {
  const { traceId } = match.params;

  const { isLoading, traceSummary } = useSelector((state) => ({
    isLoading: state.traces.traces[traceId]?.isLoading || false,
    traceSummary: state.traces.traces[traceId]?.adjustedTrace || undefined,
  }));

  const dispatch = useDispatch();

  useEffect(() => {
    dispatch(loadTrace(traceId));
  }, [traceId, dispatch]);

  if (isLoading) {
    return <LoadingIndicator />;
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
});

TracePageImpl.propTypes = propTypes;

export default withRouter(TracePageImpl);
