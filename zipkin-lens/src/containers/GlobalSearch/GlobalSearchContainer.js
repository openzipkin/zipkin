import { connect } from 'react-redux';
import { withRouter } from 'react-router';

import GlobalSearch from '../../components/GlobalSearch';
import { fetchSpans } from '../../actions/spans-action';
import { fetchServices } from '../../actions/services-action';
import { fetchTraces } from '../../actions/traces-action';

const mapStateToProps = state => ({
  spans: state.spans.spans,
  services: state.services.services,
});

const mapDispatchToProps = dispatch => ({
  fetchServices: () => dispatch(fetchServices()),
  fetchSpans: serviceName => dispatch(fetchSpans(serviceName)),
  fetchTraces: params => dispatch(fetchTraces(params)),
});

const GlobalSearchContainer = connect(
  mapStateToProps,
  mapDispatchToProps,
)(GlobalSearch);

export default withRouter(GlobalSearchContainer);
