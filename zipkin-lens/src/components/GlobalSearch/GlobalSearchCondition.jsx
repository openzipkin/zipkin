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

import GlobalSearchConditionKey from './GlobalSearchConditionKey';
import GlobalSearchNameCondition from './GlobalSearchNameCondition';
import { buildConditionKeyOptions } from './util';
import { globalSearchConditionsPropTypes } from '../../prop-types';

const propTypes = {
  conditionIndex: PropTypes.number.isRequired,

  services: PropTypes.arrayOf(PropTypes.string).isRequired,
  remoteServices: PropTypes.arrayOf(PropTypes.string).isRequired,
  spans: PropTypes.arrayOf(PropTypes.string).isRequired,
  autocompleteKeys: PropTypes.arrayOf(PropTypes.string).isRequired,
  autocompleteValues: PropTypes.arrayOf(PropTypes.string).isRequired,

  conditions: globalSearchConditionsPropTypes.isRequired,
  changeConditionKey: PropTypes.func.isRequired,
  changeConditionValue: PropTypes.func.isRequired,
  deleteCondition: PropTypes.func.isRequired,

  fetchSpans: PropTypes.func.isRequired,
  fetchRemoteServices: PropTypes.func.isRequired,
  fetchAutocompleteKeys: PropTypes.func.isRequired,
  fetchAutocompleteValues: PropTypes.func.isRequired,
};

class GlobalSearchCondition extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      isKeyFocused: false,
      isValueFocused: false,
    };

    this.handleKeyFocus = this.handleKeyFocus.bind(this);
    this.handleKeyBlur = this.handleKeyBlur.bind(this);
    this.handleKeyChange = this.handleKeyChange.bind(this);
    this.handleValueFocus = this.handleValueFocus.bind(this);
    this.handleValueBlur = this.handleValueBlur.bind(this);
    this.handleValueChange = this.handleValueChange.bind(this);
    this.handleDeleteButtonClick = this.handleDeleteButtonClick.bind(this);
  }

  getConditionKey() {
    const { conditions, conditionIndex } = this.props;
    return conditions[conditionIndex].key;
  }

  handleKeyFocus() {
    this.setState({ isKeyFocused: true });
  }

  handleKeyBlur() {
    this.setState({ isKeyFocused: false });
  }

  handleKeyChange() {
    const {
      conditionIndex,
      autocompleteKeys,
      changeConditionKey,
      fetchAutocompleteValues,
    } = this.props;

    const conditionKey = this.getConditionKey();

    changeConditionKey(conditionIndex, conditionKey);

    if (autocompleteKeys.includes(conditionKey)) {
      fetchAutocompleteValues(conditionKey);
    }
  }

  handleValueFocus() {
    this.setState({ isValueFocused: true });
  }

  handleValueBlur() {
    this.setState({ isValueFocused: false });
  }

  handleValueChange(value) {
    const {
      conditionIndex,
      fetchRemoteServices,
      fetchSpans,
      changeConditionValue,
    } = this.props;

    changeConditionValue(conditionIndex, value);

    if (this.getConditionKey() === 'serviceName') {
      fetchRemoteServices(value);
      fetchSpans(value);
    }
  }

  handleDeleteButtonClick() {
    const { deleteCondition, conditionIndex } = this.props;
    deleteCondition(conditionIndex);
  }

  isFocused() {
    const { isKeyFocused, isValueFocused } = this.state;
    return isKeyFocused || isValueFocused;
  }

  renderConditionValue() {
    const {
      services,
      remoteServices,
      spans,
      autocompleteValues,
    } = this.props;

    const conditionKey = this.getConditionKey();

    const commonProps = {
      onChange: this.handleValueChange,
    };

    switch (conditionKey) {
      case 'serviceName':
      case 'remoteServiceName':
      case 'spanName': {
        let options;
        if (conditionKey === 'serviceName') {
          options = services;
        } else if (conditionKey === 'remoteServiceName') {
          options = remoteServices;
        } else if (conditionKey === 'spanName') {
          options = spans;
        }
        return (
          <GlobalSearchNameCondition
            {...commonProps}
            options={options}
            onFocus={this.handleValueFocus}
            onBlur={this.handleValueBlur}
            setFocusableElement={this.setFocusableElement}
            isFocused={this.isFocused()}
          />
        );
      }
      default: // autocompleteTags
        return (
          <GlobalSearchNameCondition
            {...commonProps}
            options={autocompleteValues}
            onFocus={this.handleValueFocus}
            onBlur={this.handleValueBlur}
            setFocusableElement={this.setFocusableElement}
            isFocused={this.isFocused()}
          />
        );
    }
  }

  render() {
    const { conditions, autocompleteKeys } = this.props;
    const { isKeyFocused } = this.state;

    const conditionKey = this.getConditionKey();

    return (
      <div className="global-search-condition">
        <div className="global-search-condition__key-wrapper">
          <GlobalSearchConditionKey
            conditionKey={conditionKey}
            conditionKeyOptions={
              buildConditionKeyOptions(conditionKey, conditions, autocompleteKeys)
            }
            isFocused={isKeyFocused}
            onFocus={this.handleKeyFocus}
            onBlur={this.handleKeyBlur}
            onChange={this.handkeKeyChange}
          />
        </div>
        <div className="global-search-condition__value-wrapper">
          {this.renderConditionValue()}
        </div>
        <div className="global-search-condition__delete-button-wrapper">
          <button
            type="button"
            onClick={this.handleDeleteButtonClick}
            className="global-search-condition__delete-button"
          >
            <span className="fas fa-times global-search-condition__delete-button-icon" />
          </button>
        </div>
      </div>
    );
  }
}

GlobalSearchCondition.propTypes = propTypes;

export default GlobalSearchCondition;
