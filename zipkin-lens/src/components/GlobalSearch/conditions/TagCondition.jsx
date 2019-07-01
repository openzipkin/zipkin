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
import React, { useCallback } from 'react';
import { makeStyles } from '@material-ui/styles';
import InputBase from '@material-ui/core/InputBase';
import { theme } from '../../../colors';

const useStyles = makeStyles({
  valueInput: {
    width: '15rem',
    height: '2.4rem',
    display: 'flex',
    alignItems: 'center',
    color: theme.palette.primary.contrastText,
    padding: '0 0.4rem',
  },
});

const propTypes = {
  value: PropTypes.string.isRequired,
  onChange: PropTypes.func.isRequired,
  onFocus: PropTypes.func.isRequired,
  onBlur: PropTypes.func.isRequired,
  isFocused: PropTypes.bool.isRequired,
  valueRef: PropTypes.shape({}).isRequired,
  addCondition: PropTypes.func.isRequired,
};

const TagCondition = ({
  value,
  onChange,
  onFocus,
  onBlur,
  isFocused,
  valueRef,
  addCondition,
}) => {
  const classes = useStyles();

  const handleValueChange = useCallback((event) => {
    onChange(event.target.value);
  }, [onChange]);

  const handleKeyDown = useCallback((event) => {
    if (event.key === 'Enter') {
      valueRef.current.blur();
      addCondition();
    }
  }, [addCondition, valueRef]);

  return (
    <InputBase
      inputRef={valueRef}
      value={value}
      className={classes.valueInput}
      onChange={handleValueChange}
      onFocus={onFocus}
      onBlur={onBlur}
      onKeyDown={handleKeyDown}
      style={{
        backgroundColor: isFocused ? theme.palette.primary.main : theme.palette.primary.light,
      }}
    />
  );
};

TagCondition.propTypes = propTypes;

export default TagCondition;
