import { connect } from 'react-redux';
import queryString from 'query-string';

import Trace from '../../../components/Browser/Traces';
import {
  treeCorrectedForClockSkew,
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
  const corrected = traces.map(treeCorrectedForClockSkew);
  const summaries = traceSummaries(serviceName, corrected.map(traceSummary));

  const clockSkewCorrectedTracesMap = {};
  corrected.forEach((trace) => {
    const { span } = trace;
    clockSkewCorrectedTracesMap[span.traceId] = trace;
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
