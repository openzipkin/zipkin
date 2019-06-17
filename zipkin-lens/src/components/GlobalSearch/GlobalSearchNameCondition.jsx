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
import ReactSelect from 'react-select';

import { theme } from '../../colors';

const propTypes = {
  value: PropTypes.string,
  options: PropTypes.arrayOf(PropTypes.string).isRequired,
  onFocus: PropTypes.func.isRequired,
  onBlur: PropTypes.func.isRequired,
  onChange: PropTypes.func.isRequired,
  isFocused: PropTypes.bool.isRequired,
  setFocusableElement: PropTypes.func.isRequired,
};

const defaultProps = {
  value: undefined,
};

const GlobalSearchNameCondition = ({
  value,
  options,
  isFocused,
  onFocus,
  onBlur,
  onChange,
  setFocusableElement,
}) => {
  const styles = {
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
    menuPortal: base => ({
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
  };

  return (
    <ReactSelect
      ref={setFocusableElement}
      value={{ value, label: value }}
      options={options.map(opt => ({ value: opt, label: opt }))}
      styles={styles}
      onFocus={onFocus}
      onBlur={onBlur}
      onChange={(selected) => { onChange(selected.value); }}
      blurInputOnSelect
    />
  );
};

GlobalSearchNameCondition.propTypes = propTypes;
GlobalSearchNameCondition.defaultProps = defaultProps;

export default GlobalSearchNameCondition;
