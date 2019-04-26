/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
