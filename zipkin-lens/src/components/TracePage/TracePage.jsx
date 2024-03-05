/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import PropTypes from 'prop-types';
import React, { useEffect, useRef } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { withRouter } from 'react-router-dom';

import { setAlert } from '../App/slice';
import { LoadingIndicator } from '../common/LoadingIndicator';
import { loadTrace } from '../../slices/tracesSlice';
import { TracePageContent } from './TracePageContent';

const propTypes = {
  match: PropTypes.shape({
    params: PropTypes.shape({
      traceId: PropTypes.string.isRequired,
    }),
  }).isRequired,
};

export const TracePageImpl = React.memo(({ match }) => {
  const { traceId } = match.params;

  const { isLoading, traceSummary, error, rawTrace } = useSelector((state) => ({
    isLoading: state.traces.traces[traceId]?.isLoading || false,
    traceSummary: state.traces.traces[traceId]?.adjustedTrace || undefined,
    rawTrace: state.traces.traces[traceId]?.rawTrace || undefined,
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

  if (!traceSummary || !rawTrace) {
    return null;
  }
  return <TracePageContent trace={traceSummary} rawTrace={rawTrace} />;
});

TracePageImpl.propTypes = propTypes;

export default withRouter(TracePageImpl);
