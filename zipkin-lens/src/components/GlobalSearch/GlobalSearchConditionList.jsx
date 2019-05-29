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

import { retrieveNextConditionKey, retrieveDefaultConditionValue } from './util';
import GlobalSearchConditionContainer from '../../containers/GlobalSearch/GlobalSearchConditionContainer';
import { globalSearchConditionsPropTypes } from '../../prop-types';

const propTypes = {
  conditions: globalSearchConditionsPropTypes.isRequired,
  addCondition: PropTypes.func.isRequired,
  autocompleteKeys: PropTypes.arrayOf(PropTypes.string).isRequired,
};

class GlobalSearchConditionList extends React.Component {
  constructor(props) {
    super(props);

    this.handleAddButtonClick = this.handleAddButtonClick.bind(this);
  }

  handleAddButtonClick(event) {
    const { addCondition, conditions, autocompleteKeys } = this.props;
    const nextConditionKey = retrieveNextConditionKey(conditions, autocompleteKeys);

    addCondition({
      key: nextConditionKey,
      value: retrieveDefaultConditionValue(nextConditionKey),
    });

    event.stopPropagation();
  }

  render() {
    const { conditions } = this.props;

    return (
      <div className="global-search-condition-list">
        {
          conditions.length === 0
            ? (
              <div className="global-search-condition-list__empty-message">
                Please select the criteria for your trace lookup.
              </div>
            )
            : conditions.map((condition, conditionIndex) => (
              <GlobalSearchConditionContainer conditionIndex={conditionIndex} />
            ))
        }
        <div className="global-search-condition-list__add-button-wrapper">
          <button
            type="button"
            className="global-search-condition-list__add-button"
            onClick={this.handleAddButtonClick}
          >
            <span className="fas fa-plus global-search-condition-list__add-button-icon" />
          </button>
        </div>
      </div>
    );
  }
}

GlobalSearchConditionList.propTypes = propTypes;

export default GlobalSearchConditionList;
