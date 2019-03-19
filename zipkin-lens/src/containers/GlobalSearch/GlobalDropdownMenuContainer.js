import { connect } from 'react-redux';

import GlobalDropdownMenu from '../../components/GlobalSearch/GlobalDropdownMenu';
import { loadTrace } from '../../actions/trace-viewer-action';

const mapDispatchToProps = dispatch => ({
  loadTrace: trace => dispatch(loadTrace(trace)),
});

const GlobalDropdownMenuContainer = connect(
  null,
  mapDispatchToProps,
)(GlobalDropdownMenu);

export default GlobalDropdownMenuContainer;
