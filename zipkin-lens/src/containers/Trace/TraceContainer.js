import { connect } from 'react-redux';

import { fetchTrace } from '../../actions/trace-action';
import Trace from '../../components/Trace';
import { getDetailedTraceSummary } from '../../zipkin';

const mapStateToProps = (state, ownProps) => {
  const props = {
    isLoading: state.trace.isLoading,
    traceId: ownProps.match.params.traceId,
  };
  if (state.trace.trace.length === 0) {
    props.traceSummary = null;
  } else {
    props.traceSummary = getDetailedTraceSummary(state.trace.trace);
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
