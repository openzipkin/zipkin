import { connect } from 'react-redux';

import Browser from '../../components/Browser';
import { clearTraces, fetchTraces } from '../../actions/traces-action';

const mapDispatchToProps = dispatch => ({
  fetchTraces: params => dispatch(fetchTraces(params)),
  clearTraces: () => dispatch(clearTraces()),
});

const BrowserContainer = connect(
  null,
  mapDispatchToProps,
)(Browser);

export default BrowserContainer;
