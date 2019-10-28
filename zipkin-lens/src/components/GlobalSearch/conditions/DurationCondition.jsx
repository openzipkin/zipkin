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
import React, { useState, useCallback, useMemo } from 'react';
import ReactSelect from 'react-select';
import { makeStyles } from '@material-ui/styles';
import Box from '@material-ui/core/Box';
import InputBase from '@material-ui/core/InputBase';

import { theme } from '../../../colors';

const unitOptions = ['μs', 'ms', 's'];

const useStyles = makeStyles({
  valueInput: {
    width: '4rem',
    height: '2.4rem',
    display: 'flex',
    alignItems: 'center',
    color: theme.palette.primary.contrastText,
    padding: '0 0.4rem',
  },
});

const propTypes = {
  value: PropTypes.number.isRequired,
  onChange: PropTypes.func.isRequired,
  onFocus: PropTypes.func.isRequired,
  onBlur: PropTypes.func.isRequired,
  isFocused: PropTypes.bool.isRequired,
  valueRef: PropTypes.shape({}).isRequired,
  addCondition: PropTypes.func.isRequired,
};

const initialUnit = (value) => {
  if (value === 0) {
    return 'μs';
  }
  if (value % (1000 * 1000) === 0) {
    return 's';
  }
  if (value % (1000) === 0) {
    return 'ms';
  }
  return 'μs';
};

const DurationCondition = ({
  value,
  onChange,
  onFocus,
  onBlur,
  isFocused,
  valueRef,
  addCondition,
}) => {
  const classes = useStyles();

  const [unit, setUnit] = useState(initialUnit(value));

  const handleValueChange = useCallback((event) => {
    let newValue = parseInt(event.target.value, 10);
    if (Number.isNaN(newValue)) {
      newValue = 0;
    }
    switch (unit) {
      case 'μs': onChange(newValue); break;
      case 'ms': onChange(newValue * 1000); break;
      case 's': onChange(newValue * 1000 * 1000); break;
      default: break;
    }
  }, [onChange, unit]);

  const handleUnitChange = useCallback((selected) => {
    const prevUnit = unit;
    const newUnit = selected.value;
    setUnit(newUnit);
    switch (prevUnit) {
      case 'μs':
        switch (newUnit) {
          case 'ms': onChange(value * 1000); break;
          case 's': onChange(value * 1000 * 1000); break;
          default: break; // Do nothing
        }
        break;
      case 'ms':
        switch (newUnit) {
          case 'μs': onChange(value / 1000); break;
          case 's': onChange(value * 1000); break;
          default: break; // Do nothing
        }
        break;
      case 's':
        switch (newUnit) {
          case 'μs': onChange(value / (1000 * 1000)); break;
          case 'ms': onChange(value / 1000); break;
          default: break; // Do nothing
        }
        break;
      default: break; // Do nothing
    }
  }, [onChange, unit, value]);

  const handleKeyDown = useCallback((event) => {
    if (event.key === 'Enter') {
      valueRef.current.blur();
      addCondition();
    }
  }, [addCondition, valueRef]);

  const displayedValue = useMemo(() => {
    switch (unit) {
      case 'μs': return value;
      case 'ms': return value / 1000;
      case 's': return value / (1000 * 1000);
      default: return null;
    }
  }, [unit, value]);

  const styles = useMemo(() => ({
    control: base => ({
      ...base,
      width: '3rem',
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
      width: '3rem',
    }),
    singleValue: base => ({
      ...base,
      color: theme.palette.primary.contrastText,
    }),
    indicatorsContainer: base => ({
      ...base,
      display: 'none',
    }),
  }), [isFocused]);

  return (
    <Box display="flex" alignItems="center">
      <InputBase
        inputRef={valueRef}
        value={displayedValue}
        className={classes.valueInput}
        onFocus={onFocus}
        onBlur={onBlur}
        onChange={handleValueChange}
        onKeyDown={handleKeyDown}
        style={{
          backgroundColor: isFocused ? theme.palette.primary.main : theme.palette.primary.light,
        }}
      />
      <ReactSelect
        isSearchable={false}
        options={unitOptions.map(opt => ({ value: opt, label: opt }))}
        onChange={handleUnitChange}
        styles={styles}
        value={{ value: unit, label: unit }}
      />
    </Box>
  );
};

DurationCondition.propTypes = propTypes;

export default DurationCondition;
