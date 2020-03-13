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
import { connect } from 'react-redux';
import { withRouter } from 'react-router-dom';
import Box from '@material-ui/core/Box';
import CircularProgress from '@material-ui/core/CircularProgress';

import TraceSummary from './TraceSummary';
import TraceSummaryHeader from './TraceSummaryHeader';
import MessageBar from './MessageBar';
import { detailedTraceSummaryPropTypes } from '../../prop-types';
import { detailedTraceSummary, treeCorrectedForClockSkew } from '../../zipkin';
import * as traceActionCreators from '../../actions/trace-action';

const propTypes = {
  traceId: PropTypes.string,
  traceSummary: detailedTraceSummaryPropTypes,
  loadTrace: PropTypes.func.isRequired,
  isTraceViewerPage: PropTypes.bool.isRequired,
  isLoading: PropTypes.bool,
  isMalformedFile: PropTypes.bool,
  errorMessage: PropTypes.string,
  correctedTraceMap: PropTypes.shape({}),
};

const defaultProps = {
  traceId: null,
  traceSummary: null,
  isLoading: false,
  isMalformedFile: false,
  errorMessage: '',
  correctedTraceMap: {},
};

export const TracePageImpl = React.memo(
  ({
    traceId,
    traceSummary,
    loadTrace,
    isTraceViewerPage,
    isLoading,
    isMalformedFile,
    errorMessage,
    correctedTraceMap,
  }) => {
    useEffect(() => {
      if (!isTraceViewerPage) {
        loadTrace(traceId, correctedTraceMap);
      }
    }, [traceId, isTraceViewerPage, loadTrace, correctedTraceMap]);

    if (isTraceViewerPage && isMalformedFile) {
      return (
        <>
          <TraceSummaryHeader />
          <MessageBar
            variant="error"
            message={errorMessage || 'Loading error'}
          />
        </>
      );
    }

    if (!isTraceViewerPage && isLoading) {
      return (
        <>
          <TraceSummaryHeader />
          <Box width="100%" display="flex" justifyContent="center">
            <CircularProgress data-testid="progress-indicator" />
          </Box>
        </>
      );
    }

    if (!traceSummary && isTraceViewerPage) {
      return (
        <>
          <TraceSummaryHeader />
          <MessageBar variant="info" message="You need to upload JSON..." />
        </>
      );
    }

    if (!traceSummary && !isTraceViewerPage) {
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
  const { location, match } = ownProps;

  const isTraceViewerPage = location.pathname === '/traceViewer';

  const props = {};

  if (isTraceViewerPage) {
    props.isMalformedFile = state.traceViewer.isMalformedFile;
    props.errorMessage = state.traceViewer.errorMessage;
    if (state.traceViewer.trace) {
      props.traceSummary = detailedTraceSummary(
        treeCorrectedForClockSkew(state.traceViewer.trace),
      );
    } else {
      props.traceSummary = null;
    }
  } else {
    props.traceId = match.params.traceId;
    props.traceSummary = state.trace.traceSummary;
    props.isLoading = state.trace.isLoading;
    props.correctedTraceMap = state.traces.correctedTraceMap;
  }
  props.isTraceViewerPage = isTraceViewerPage;
  return props;
};

const mapDispatchToProps = (dispatch) => ({
  loadTrace: (traceId, correctedTraceMap) =>
    dispatch(traceActionCreators.loadTrace(traceId, correctedTraceMap)),
});

export default withRouter(
  connect(mapStateToProps, mapDispatchToProps)(TracePageImpl),
);
