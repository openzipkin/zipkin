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
import React, { useEffect, useRef } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { withRouter } from 'react-router-dom';

import TraceSummary from './TraceSummary';
import { setAlert } from '../App/slice';
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

  const { isLoading, traceSummary, error } = useSelector((state) => ({
    isLoading: state.traces.traces[traceId]?.isLoading || false,
    traceSummary: state.traces.traces[traceId]?.adjustedTrace || undefined,
    error: state.traces.traces[traceId]?.error || undefined,
  }));

  const dispatch = useDispatch();

  useEffect(() => {
    dispatch(loadTrace(traceId));
  }, [traceId, dispatch]);

  const firstUpdate = useRef(true);
  useEffect(() => {
    // In the first rendering, skip this useEffect, because isLoading
    // is always false and traceSummary always is undefined.
    if (firstUpdate.current) {
      firstUpdate.current = false;
      return;
    }
    if (!isLoading && !traceSummary) {
      let message = 'No trace found';
      if (error && error.message) {
        message += `: ${error.message}`;
      }
      dispatch(
        setAlert({
          message,
          severity: 'error',
        }),
      );
    }
  }, [dispatch, error, isLoading, traceSummary]);

  if (isLoading) {
    return <LoadingIndicator />;
  }

  if (!traceSummary) {
    return null;
  }
  return <TraceSummary traceSummary={traceSummary} />;
});

TracePageImpl.propTypes = propTypes;

export default withRouter(TracePageImpl);
