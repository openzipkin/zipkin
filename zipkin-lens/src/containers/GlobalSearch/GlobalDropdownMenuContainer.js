import { connect } from 'react-redux';

import GlobalDropdownMenu from '../../components/GlobalSearch/GlobalDropdownMenu';
import { loadTrace, loadTraceFailure } from '../../actions/trace-viewer-action';

const mapDispatchToProps = dispatch => ({
  loadTrace: trace => dispatch(loadTrace(trace)),
  loadTraceFailure: errorMessage => dispatch(loadTraceFailure(errorMessage)),
});

const GlobalDropdownMenuContainer = connect(
  null,
  mapDispatchToProps,
)(GlobalDropdownMenu);

export default GlobalDropdownMenuContainer;
