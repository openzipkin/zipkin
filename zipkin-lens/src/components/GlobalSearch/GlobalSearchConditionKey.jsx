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
import { connect } from 'react-redux';
import ReactSelect from 'react-select';

import { buildConditionKeyOptions, retrieveDefaultConditionValue } from './util';
import { globalSearchConditionsPropTypes } from '../../prop-types';
import * as globalSearchActionCreators from '../../actions/global-search-action';
import * as autocompleteValuesActionCreators from '../../actions/autocomplete-values-action';
import { theme } from '../../colors';

const propTypes = {
  focusValue: PropTypes.func.isRequired,
  autocompleteKeys: PropTypes.arrayOf(PropTypes.string).isRequired,
  conditionIndex: PropTypes.number.isRequired,
  conditions: globalSearchConditionsPropTypes.isRequired,
  isFocused: PropTypes.bool.isRequired,
  onFocus: PropTypes.func.isRequired,
  onBlur: PropTypes.func.isRequired,
  onChange: PropTypes.func.isRequired,
  clearConditionValue: PropTypes.func.isRequired,
  fetchAutocompleteValues: PropTypes.func.isRequired,
};

const GlobalSearchConditionKey = ({
  focusValue,
  autocompleteKeys,
  conditionIndex,
  conditions,
  isFocused,
  onFocus,
  onBlur,
  onChange,
  clearConditionValue,
  fetchAutocompleteValues,
}) => {
  const { key: conditionKey } = conditions[conditionIndex];

  const handleKeyChange = (selected) => {
    const key = selected.value;
    onChange(conditionIndex, key);
    clearConditionValue(conditionIndex, key);
    if (autocompleteKeys.includes(key)) {
      fetchAutocompleteValues(key);
    }
    focusValue();
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

  const styles = {
    control: base => ({
      ...base,
      width: isFocused
        ? '15rem'
        : '12rem',
      height: '2.4rem',
      minHeight: '2.4rem',
      border: 0,
      borderTopLeftRadius: '0.2rem',
      borderTopRightRadius: 0,
      borderBottomLeftRadius: '0.2rem',
      borderBottomRightRadius: 0,
      backgroundColor: isFocused ? theme.palette.primary.dark : theme.palette.primary.main,
      '&:hover': {
        backgroundColor: theme.palette.primary.dark,
      },
      cursor: 'pointer',
    }),
    menuPortal: base => ({
      ...base,
      zIndex: 10000,
      width: '15rem',
    }),
    singleValue: base => ({
      ...base,
      color: theme.palette.primary.contrastText,
    }),
    indicatorsContainer: base => ({
      ...base,
      display: 'none',
    }),
    input: base => ({
      ...base,
      color: theme.palette.primary.contrastText,
    }),
  };

  return (
    <ReactSelect
      autoFocus
      isSearchable={false}
      value={{ value: conditionKey, label: conditionKey }}
      options={options}
      onFocus={onFocus}
      onBlur={onBlur}
      onChange={handleKeyChange}
      styles={styles}
      defaultMenuIsOpen
      backspaceRemovesValue
    />
  );
};

GlobalSearchConditionKey.propTypes = propTypes;

const mapStateToProps = state => ({
  autocompleteKeys: state.autocompleteKeys.autocompleteKeys,
  conditions: state.globalSearch.conditions,
});

const mapDispatchToProps = (dispatch) => {
  const { changeConditionKey, changeConditionValue } = globalSearchActionCreators;
  const { fetchAutocompleteValues } = autocompleteValuesActionCreators;

  return {
    onChange: (idx, key) => dispatch(changeConditionKey(idx, key)),
    clearConditionValue: (idx, key) => dispatch(changeConditionValue(
      idx,
      retrieveDefaultConditionValue(key),
    )),
    fetchAutocompleteValues: key => dispatch(fetchAutocompleteValues(key)),
  };
};

export default connect(
  mapStateToProps,
  mapDispatchToProps,
)(GlobalSearchConditionKey);
