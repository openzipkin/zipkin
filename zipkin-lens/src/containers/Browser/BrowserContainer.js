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

  const skewCorrectedTracesMap = {};
  correctedTraces.forEach((trace) => {
    const { span } = trace;
    if (span.traceId) {
      skewCorrectedTracesMap[span.traceId] = trace;
    }
  });

  return {
    traceSummaries: correctedSummaries,
    skewCorrectedTracesMap,
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
