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
import PropTypes from 'prop-types';
import React from 'react';
import { withRouter } from 'react-router';
import ReactSelect from 'react-select';
import queryString from 'query-string';
import moment from 'moment';

import DependenciesGraph from './DependenciesGraph';
import DependenciesSidebar from './DependenciesSidebar';
import DatePicker from '../Common/DatePicker';
import LoadingOverlay from '../Common/LoadingOverlay';
import { buildQueryParameters } from '../../util/api';

const propTypes = {
  location: PropTypes.shape({}).isRequired,
  isLoading: PropTypes.bool.isRequired,
  graph: PropTypes.shape({}).isRequired,
  fetchDependencies: PropTypes.func.isRequired,
  clearDependencies: PropTypes.func.isRequired,
  history: PropTypes.shape({
    push: PropTypes.func.isRequired,
  }).isRequired,
};

export class Dependencies extends React.Component { // export for testing without withRouter
  constructor(props) {
    super(props);

    this.state = {
      startTs: moment().subtract(1, 'days'),
      endTs: moment(),
      selectedServiceName: '',
      filter: '',
    };

    this.handleStartTsChange = this.handleStartTsChange.bind(this);
    this.handleEndTsChange = this.handleEndTsChange.bind(this);
    this.handleServiceSelect = this.handleServiceSelect.bind(this);
    this.handleFilterChange = this.handleFilterChange.bind(this);
    this.handleAnalyzeButtonClick = this.handleAnalyzeButtonClick.bind(this);
  }

  componentDidMount() {
    const { location } = this.props;

    const queryParams = queryString.parse(location.search);
    const endTs = queryParams.endTs ? moment(parseInt(queryParams.endTs, 10)) : moment();
    const lookback = queryParams.lookback
      ? moment.duration(parseInt(queryParams.lookback, 10))
      : moment.duration(1, 'days');
    const startTs = endTs.clone().subtract(lookback); // subtract is not immutable.
    this.setState({
      startTs,
      endTs,
    });
    this.fetchDependencies(location);
  }

  componentWillUnmount() {
    const { clearDependencies } = this.props;
    clearDependencies();
  }

  fetchDependencies(location) {
    const { fetchDependencies } = this.props;
    if (location.search !== '' && location.search !== '?') {
      const queryParameters = queryString.parse(location.search);
      fetchDependencies(queryParameters);
    }
  }

  handleStartTsChange(startTs) {
    this.setState({ startTs });
  }

  handleEndTsChange(endTs) {
    this.setState({ endTs });
  }

  handleServiceSelect(selectedServiceName) {
    this.setState({ selectedServiceName });
  }

  handleFilterChange(filter) {
    this.setState({ filter });
  }

  handleAnalyzeButtonClick() {
    const { startTs, endTs } = this.state;
    const { history } = this.props;
    const queryParameters = buildQueryParameters({
      endTs: endTs.valueOf(),
      lookback: endTs.valueOf() - startTs.valueOf(),
    });
    const location = {
      pathname: '/zipkin/dependency',
      search: queryParameters,
    };
    history.push(location);
    this.fetchDependencies(location);
  }

  renderSearch() {
    const { startTs, endTs } = this.state;
    return (
      <div className="dependencies__search">
        <div className="dependencies__lookback-condition">
          <DatePicker
            onChange={this.handleStartTsChange}
            selected={startTs}
          />
        </div>
        <div className="dependencies__lookback-condition-separator">
          -
        </div>
        <div className="dependencies__lookback-condition">
          <DatePicker
            onChange={this.handleEndTsChange}
            selected={endTs}
          />
        </div>
        <div className="dependencies__analyze-button-wrapper">
          <div
            role="presentation"
            onClick={this.handleAnalyzeButtonClick}
            className="dependencies__analyze-button"
          >
            Analyze Dependencies
          </div>
        </div>
      </div>
    );
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
          <div className="dependencies__search-wrapper">
            {this.renderSearch()}
          </div>
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
