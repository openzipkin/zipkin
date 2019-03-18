import { connect } from 'react-redux';

import TraceViewer from '../../components/TraceViewer';
import { treeCorrectedForClockSkew, detailedTraceSummary } from '../../zipkin';

const mapStateToProps = (state) => {
  if (state.traceViewer.trace) {
    return {
      traceSummary: detailedTraceSummary(
        treeCorrectedForClockSkew(state.traceViewer.trace),
      ),
    };
  }
  return {
    traceSummary: null,
  };
};

const TraceViewerContainer = connect(
  mapStateToProps,
)(TraceViewer);

export default TraceViewerContainer;
