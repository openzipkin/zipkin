import { connect } from 'react-redux';

import Dependencies from '../../components/Dependencies'; // eslint-disable-line import/no-named-as-default
import { fetchDependencies, clearDependencies } from '../../actions/dependencies-action';
import Graph from '../../util/dependencies-graph';

const mapStateToProps = state => ({
  isLoading: state.dependencies.isLoading,
  graph: new Graph(state.dependencies.dependencies),
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
