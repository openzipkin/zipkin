import { connect } from 'react-redux';

import GlobalDropdownMenu from '../../components/GlobalSearch/GlobalDropdownMenu';
import { setTrace } from '../../actions/trace-viewer-action';

const mapDispatchToProps = dispatch => ({
  setTrace: trace => dispatch(setTrace(trace)),
});

const GlobalDropdownMenuContainer = connect(
  null,
  mapDispatchToProps,
)(GlobalDropdownMenu);

export default GlobalDropdownMenuContainer;
