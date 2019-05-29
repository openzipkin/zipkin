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

import GlobalSearchConditionListContainer from '../../containers/GlobalSearch/GlobalSearchConditionListContainer';
import { buildTracesQueryParameters, buildTracesApiQueryParameters } from './api';
import { globalSearchConditionsPropTypes, globalSearchLookbackConditionPropTypes } from '../../prop-types';

const propTypes = {
  history: PropTypes.shape({ push: PropTypes.func.isRequired }).isRequired,
  conditions: globalSearchConditionsPropTypes.isRequired,
  lookbackCondition: globalSearchLookbackConditionPropTypes.isRequired,
  limitCondition: PropTypes.number.isRequired,
  fetchTraces: PropTypes.func.isRequired,
};

class GlobalSearch extends React.Component {
  constructor(props) {
    super(props);
    this.handleFindButtonClick = this.handleFindButtonClick.bind(this);
  }

  handleFindButtonClick(event) {
    const {
      history,
      conditions,
      lookbackCondition,
      limitCondition,
    } = this.props;

    const queryParameters = buildTracesQueryParameters(
      conditions,
      lookbackCondition,
      limitCondition,
    );
    const location = { pathname: '/zipkin', search: queryParameters };
    history.push(location);
    this.fetchTraces(buildTracesApiQueryParameters(
      conditions,
      lookbackCondition,
      limitCondition,
    ));
    event.stopPropagation();
  }

  fetchTraces(queryParameters) {
    const { fetchTraces } = this.props;
    fetchTraces(queryParameters);
  }

  render() {
    return (
      <div className="global-search">
        <div className="global-search__condition-list-wrapper">
          <GlobalSearchConditionListContainer />
        </div>
        <div className="global-search__find-button-wrapper">
          <button
            type="button"
            className="global-search__find-button"
            onClick={this.handleFindButtonClick}
          >
            <span className="fas fa-search global-search__find-button-icon" />
          </button>
        </div>
      </div>
    );
  }
}

GlobalSearch.propTypes = propTypes;

export default withRouter(GlobalSearch);
