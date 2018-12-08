import { connect } from 'react-redux';

import { fetchTrace } from '../../actions/trace-action';
import Trace from '../../components/Trace';
import { treeCorrectedForClockSkew, detailedTraceSummary } from '../../zipkin';

const mapStateToProps = (state, ownProps) => {
  const props = {
    isLoading: state.trace.isLoading,
    traceId: ownProps.match.params.traceId,
  };
  if (state.trace.trace.length === 0) {
    props.traceSummary = null;
  } else {
    const corrected = treeCorrectedForClockSkew(state.trace.trace);
    props.traceSummary = detailedTraceSummary(corrected);
  }
  return props;
};

const mapDispatchToProps = dispatch => ({
  fetchTrace: traceId => dispatch(fetchTrace(traceId)),
});

const TraceContainer = connect(
  mapStateToProps,
  mapDispatchToProps,
)(Trace);

export default TraceContainer;
