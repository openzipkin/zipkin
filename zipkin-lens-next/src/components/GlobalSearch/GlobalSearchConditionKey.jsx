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
import React, { useMemo, useCallback } from 'react';
import { useSelector, useDispatch } from 'react-redux';
import ReactSelect from 'react-select';

import { buildConditionKeyOptions, retrieveDefaultConditionValue } from './util';
import { changeConditionKey, changeConditionValue } from '../../actions/global-search-action';
import { fetchAutocompleteValues } from '../../actions/autocomplete-values-action';
import { theme } from '../../colors';

const propTypes = {
  focusValue: PropTypes.func.isRequired,
  conditionIndex: PropTypes.number.isRequired,
  isFocused: PropTypes.bool.isRequired,
  onFocus: PropTypes.func.isRequired,
  onBlur: PropTypes.func.isRequired,
};

const GlobalSearchConditionKey = ({
  focusValue,
  conditionIndex,
  isFocused,
  onFocus,
  onBlur,
}) => {
  const dispatch = useDispatch();

  const autocompleteKeys = useSelector(state => state.autocompleteKeys.autocompleteKeys);
  const conditions = useSelector(state => state.globalSearch.conditions);

  const { key: conditionKey } = conditions[conditionIndex];

  const clearConditionValue = useCallback(
    (idx, key) => dispatch(changeConditionValue(
      idx,
      retrieveDefaultConditionValue(key),
    )),
    [dispatch],
  );

  const handleKeyChange = useCallback(
    (selected) => {
      const key = selected.value;
      dispatch(changeConditionKey(conditionIndex, key));
      clearConditionValue(conditionIndex, key);
      if (autocompleteKeys.includes(key)) {
        dispatch(fetchAutocompleteValues(key));
      }
      focusValue();
    },
    [autocompleteKeys, clearConditionValue, conditionIndex, dispatch, focusValue],
  );

  const options = useMemo(
    () => buildConditionKeyOptions(
      conditionKey,
      conditions,
      autocompleteKeys,
    ).map(opt => ({
      value: opt.conditionKey,
      label: opt.conditionKey,
      isDisabled: opt.isDisabled,
    })),
    [conditionKey, conditions, autocompleteKeys],
  );

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
    menu: base => ({
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

export default GlobalSearchConditionKey;
