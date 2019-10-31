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
import React, { useCallback, useMemo } from 'react';
import ReactSelect from 'react-select';

import { theme } from '../../../colors';

const propTypes = {
  value: PropTypes.string,
  options: PropTypes.arrayOf(PropTypes.string).isRequired,
  onFocus: PropTypes.func.isRequired,
  onBlur: PropTypes.func.isRequired,
  onChange: PropTypes.func.isRequired,
  isFocused: PropTypes.bool.isRequired,
  valueRef: PropTypes.shape({}).isRequired,
  addCondition: PropTypes.func.isRequired,
  isLoading: PropTypes.bool.isRequired,
};

const defaultProps = {
  value: undefined,
};

const NameCondition = ({
  value,
  options,
  isFocused,
  onFocus,
  onBlur,
  onChange,
  valueRef,
  addCondition,
  isLoading,
}) => {
  const styles = useMemo(() => ({
    control: base => ({
      ...base,
      width: isFocused
        ? '18rem'
        : '15rem',
      height: '2.4rem',
      minHeight: '2.4rem',
      border: 0,
      borderRadius: 0,
      backgroundColor: isFocused ? theme.palette.primary.main : theme.palette.primary.light,
      '&:hover': {
        backgroundColor: theme.palette.primary.main,
      },
      cursor: 'pointer',
    }),
    menu: base => ({
      ...base,
      zIndex: 10000,
      width: '18rem',
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
  }), [isFocused]);

  const handleChange = useCallback((selected) => {
    onChange(selected.value);
    addCondition();
  }, [addCondition, onChange]);

  return (
    <ReactSelect
      ref={valueRef}
      value={{ value, label: value }}
      options={options.map(opt => ({ value: opt, label: opt }))}
      isLoading={isLoading}
      styles={styles}
      onFocus={onFocus}
      onBlur={onBlur}
      onChange={handleChange}
      blurInputOnSelect
      openMenuOnFocus
    />
  );
};

NameCondition.propTypes = propTypes;
NameCondition.defaultProps = defaultProps;

export default NameCondition;
