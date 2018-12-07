import { connect } from 'react-redux';

import Dependencies from '../../components/Dependencies';
import { fetchDependencies, clearDependencies } from '../../actions/dependencies-action';

const mapStateToProps = state => ({
  isLoading: state.dependencies.isLoading,
  dependencies: state.dependencies.dependencies,
});

const mapDispatchToProps = dispatch => ({
  fetchDependencies: params => dispatch(fetchDependencies(params)),
  clearDependencies: () => dispatch(clearDependencies()),
});

const DependenciesContainer = connect(
  mapStateToProps,
  mapDispatchToProps,
)(Dependencies);

export default DependenciesContainer;
