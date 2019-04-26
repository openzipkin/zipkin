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

import { sortingMethods } from './sorting';
import BrowserHeader from './BrowserHeader';
import BrowserResults from './BrowserResults';
import LoadingOverlay from '../Common/LoadingOverlay';
import { traceSummariesPropTypes } from '../../prop-types';

const propTypes = {
  traceSummaries: traceSummariesPropTypes.isRequired,
  tracesMap: PropTypes.shape({}).isRequired,
  isLoading: PropTypes.bool.isRequired,
  clearTraces: PropTypes.func.isRequired,
};

class Browser extends React.Component {
  constructor(props) {
    super(props);
    this.state = { sortingMethod: sortingMethods.LONGEST };
    this.handleSortingMethodChange = this.handleSortingMethodChange.bind(this);
  }

  componentWillUnmount() {
    const { clearTraces } = this.props;
    clearTraces();
  }

  handleSortingMethodChange(selected) {
    this.setState({ sortingMethod: selected.value });
  }

  render() {
    const { isLoading, traceSummaries, tracesMap } = this.props;
    const { sortingMethod } = this.state;
    return (
      <div className="browser">
        <LoadingOverlay active={isLoading} />
        <BrowserHeader
          numTraces={traceSummaries.length}
          sortingMethod={sortingMethod}
          onChange={this.handleSortingMethodChange}
        />
        <BrowserResults
          traceSummaries={traceSummaries}
          sortingMethod={sortingMethod}
          tracesMap={tracesMap}
        />
      </div>
    );
  }
}

Browser.propTypes = propTypes;

export default Browser;
