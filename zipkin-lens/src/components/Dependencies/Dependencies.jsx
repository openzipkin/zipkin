/*
 * Copyright 2015-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
import PropTypes from 'prop-types';
import React from 'react';
import { withRouter } from 'react-router';
import ReactSelect from 'react-select';

import DependenciesGraph from './DependenciesGraph';
import DependenciesSidebar from './DependenciesSidebar';
import LoadingOverlay from '../Common/LoadingOverlay';

const propTypes = {
  location: PropTypes.shape({}).isRequired,
  isLoading: PropTypes.bool.isRequired,
  graph: PropTypes.shape({}).isRequired,
  history: PropTypes.shape({
    push: PropTypes.func.isRequired,
  }).isRequired,
};

export class Dependencies extends React.Component { // export for testing without withRouter
  constructor(props) {
    super(props);

    this.state = {
      selectedServiceName: '',
      filter: '',
    };

    this.handleServiceSelect = this.handleServiceSelect.bind(this);
    this.handleFilterChange = this.handleFilterChange.bind(this);
  }

  handleServiceSelect(selectedServiceName) {
    this.setState({ selectedServiceName });
  }

  handleFilterChange(filter) {
    this.setState({ filter });
  }

  renderFilter() {
    const { filter } = this.state;
    const { graph } = this.props;
    const options = graph.allNodeNames().map(
      nodeName => ({ value: nodeName, label: nodeName }),
    );
    const value = !filter ? undefined : { value: filter, label: filter };
    return (
      <ReactSelect
        onChange={(selected) => { this.handleFilterChange(selected.value); }}
        options={options}
        value={value}
        styles={{
          control: provided => ({
            ...provided,
            width: '240px',
          }),
        }}
        placeholder="Filter by ..."
      />
    );
  }

  render() {
    const { isLoading, graph } = this.props;
    const { selectedServiceName, filter } = this.state;
    const isSidebarOpened = !!selectedServiceName;

    return (
      <div className="dependencies">
        <LoadingOverlay active={isLoading} />
        <div className={`dependencies__main ${
          isSidebarOpened
            ? 'dependencies__main--narrow'
            : 'dependencies__main--wide'}`}
        >
          {
            graph.allNodes().length === 0
              ? null
              : (
                <div>
                  <div className="dependencies__filter-wrapper">
                    {this.renderFilter()}
                  </div>
                  <div className="dependencies__graph-wrapper">
                    <DependenciesGraph
                      graph={graph}
                      onServiceSelect={this.handleServiceSelect}
                      selectedServiceName={selectedServiceName}
                      filter={filter}
                    />
                  </div>
                </div>
              )
          }
        </div>
        <div className={`dependencies__sidebar-wrapper ${
          isSidebarOpened
            ? 'dependencies__sidebar-wrapper--opened'
            : 'dependencies__sidebar-wrapper--closed'}`}
        >
          <DependenciesSidebar
            serviceName={selectedServiceName}
            targetEdges={graph.getTargetEdges(selectedServiceName)}
            sourceEdges={graph.getSourceEdges(selectedServiceName)}
          />
        </div>
      </div>
    );
  }
}

Dependencies.propTypes = propTypes;

export default withRouter(Dependencies);
