import { connect } from 'react-redux';

import Search from '../../../components/Browser/Sidebar/Search';
import { fetchSpans } from '../../../actions/spans-action';
import { fetchServices } from '../../../actions/services-action';

const mapStateToProps = state => ({
  spans: state.spans.spans,
  services: state.services.services,
});

const mapDispatchToProps = dispatch => ({
  fetchServices: () => dispatch(fetchServices()),
  fetchSpans: serviceName => dispatch(fetchSpans(serviceName)),
});

const SearchContainer = connect(
  mapStateToProps,
  mapDispatchToProps,
)(Search);

export default SearchContainer;
