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
