import { connect } from 'react-redux';

import TraceViewer from '../../components/TraceViewer';
import { treeCorrectedForClockSkew, detailedTraceSummary } from '../../zipkin';

const mapStateToProps = (state) => {
  if (state.traceViewer.trace) {
    return {
      traceSummary: detailedTraceSummary(
        treeCorrectedForClockSkew(state.traceViewer.trace),
      ),
      isMalformedFile: state.traceViewer.isMalformedFile,
      errorMessage: state.traceViewer.errorMessage,
    };
  }
  return {
    traceSummary: null,
    isMalformedFile: state.traceViewer.isMalformedFile,
    errorMessage: state.traceViewer.errorMessage,
  };
};

const TraceViewerContainer = connect(
  mapStateToProps,
)(TraceViewer);

export default TraceViewerContainer;
