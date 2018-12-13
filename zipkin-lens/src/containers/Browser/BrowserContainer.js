import { connect } from 'react-redux';

import Browser from '../../components/Browser';
import { clearTraces } from '../../actions/traces-action';

const mapDispatchToProps = dispatch => ({
  clearTraces: () => dispatch(clearTraces()),
});

const BrowserContainer = connect(
  null,
  mapDispatchToProps,
)(Browser);

export default BrowserContainer;
