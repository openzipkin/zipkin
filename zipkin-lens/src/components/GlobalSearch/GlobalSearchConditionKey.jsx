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
import { connect } from 'react-redux';
import ReactSelect from 'react-select';
import Box from '@material-ui/core/Box';

import { buildReactSelectStyle, buildConditionKeyOptions } from './util';
import { globalSearchConditionsPropTypes } from '../../prop-types';
import * as globalSearchActionCreators from '../../actions/global-search-action';
import * as autocompleteValuesActionCreators from '../../actions/autocomplete-values-action';

const propTypes = {
  autocompleteKeys: PropTypes.arrayOf(PropTypes.string).isRequired,
  conditionIndex: PropTypes.number.isRequired,
  conditions: globalSearchConditionsPropTypes.isRequired,
  isFocused: PropTypes.bool.isRequired,
  onFocus: PropTypes.func.isRequired,
  onBlur: PropTypes.func.isRequired,
  onChange: PropTypes.func.isRequired,
  fetchAutocompleteValues: PropTypes.func.isRequired,
};

const GlobalSearchConditionKey = ({
  autocompleteKeys,
  conditionIndex,
  conditions,
  isFocused,
  onFocus,
  onBlur,
  onChange,
  fetchAutocompleteValues,
}) => {
  const { key: conditionKey } = conditions[conditionIndex];

  const handleKeyChange = (selected) => {
    const key = selected.value;
    onChange(conditionIndex, key);
    if (autocompleteKeys.includes(key)) {
      fetchAutocompleteValues(key);
    }
  };

  const options = buildConditionKeyOptions(
    conditionKey,
    conditions,
    autocompleteKeys,
  ).map(opt => ({
    value: opt.conditionKey,
    label: opt.conditionKey,
    isDisabled: opt.isDisabled,
  }));

  const styles = buildReactSelectStyle(
    conditionKey,
    options,
    isFocused,
    12,
    'main',
  );

  return (
    <Box>
      <ReactSelect
        autoFocus
        isSearchable={false}
        value={{ value: conditionKey, label: conditionKey }}
        options={options}
        onFocus={onFocus}
        onBlur={onBlur}
        onChange={handleKeyChange}
        styles={styles}
        menuPortalTarget={document.body}
        defaultMenuIsOpen
        backspaceRemovesValue
      />
    </Box>
  );
};

/*
class GlobalSearchConditionKey extends React.Component {
  conditionKeyOptions() {
    const { conditionKeyOptions } = this.props;
    return conditionKeyOptions.map(conditionKeyOption => ({
      value: conditionKeyOption.conditionKey,
      label: conditionKeyOption.conditionKey,
      isDisabled: conditionKeyOption.isDisabled,
    }));
  }

  buildReactSelectStyle() {
    const { conditionKey, conditionKeyOptions, isFocused } = this.props;

    return buildReactSelectStyle(
      conditionKey,
      conditionKeyOptions.map(opt => opt.conditionKey),
      isFocused,
    );
  }

  render() {
    const {
      conditionKey,
      onFocus,
      onBlur,
      onChange,
    } = this.props;

    return (
      <div className="global-search-condition-key">
        <ReactSelect
          autoFocus
          isSearchable={false}
          value={{ value: conditionKey, label: conditionKey }}
          options={this.conditionKeyOptions()}
          onFocus={onFocus}
          onBlur={onBlur}
          onChange={(selected) => { onChange(selected.value); }}
          styles={this.buildReactSelectStyle()}
          menuPortalTarget={document.body}
          defaultMenuIsOpen
          backspaceRemovesValue={false}
          classNamePrefix="global-search-condition-key-select"
        />
      </div>
    );
  }
}
*/

GlobalSearchConditionKey.propTypes = propTypes;

const mapStateToProps = state => ({
  autocompleteKeys: state.autocompleteKeys.autocompleteKeys,
  conditions: state.globalSearch.conditions,
});

const mapDispatchToProps = (dispatch) => {
  const { changeConditionKey } = globalSearchActionCreators;
  const { fetchAutocompleteValues } = autocompleteValuesActionCreators;

  return {
    onChange: (idx, key) => dispatch(changeConditionKey(idx, key)),
    fetchAutocompleteValues: key => dispatch(fetchAutocompleteValues(key)),
  };
};

export default connect(
  mapStateToProps,
  mapDispatchToProps,
)(GlobalSearchConditionKey);
