/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
import React, { useState, useRef, useCallback } from 'react';
import { useIntl } from 'react-intl';
import { useSelector, useDispatch } from 'react-redux';
import { makeStyles } from '@material-ui/styles';
import InputBase from '@material-ui/core/InputBase';
import Tooltip from '@material-ui/core/Tooltip';

import { setLimitCondition } from '../../../actions/global-search-action';
import { useMount } from '../../../hooks';

import messages from '../messages';

const useStyles = makeStyles(theme => ({
  input: {
    width: '6rem',
    padding: '0 0.5rem',
    color: theme.palette.secondary.contrastText,
    backgroundColor: theme.palette.primary.main,
    '&:hover': {
      backgroundColor: theme.palette.primary.dark,
    },
    '&:focus-within': {
      backgroundColor: theme.palette.primary.dark,
    },
  },
}));

const LimitCondition = () => {
  const classes = useStyles();
  const intl = useIntl();

  const dispatch = useDispatch();

  const limitCondition = useSelector(state => state.globalSearch.limitCondition);
  const limitConditionRef = useRef();
  limitConditionRef.current = limitCondition;

  const inputRef = useRef(null);

  const [value, setValue] = useState(limitCondition);

  useMount(() => {
    setTimeout(() => setValue(limitConditionRef.current), 0);
  });

  const handleValueChange = useCallback((event) => {
    if (event.target.value === '') {
      dispatch(setLimitCondition(0));
    } else {
      dispatch(setLimitCondition(parseInt(event.target.value, 10)));
    }
    setValue(event.target.value);
  }, [dispatch]);

  const handleKeyDown = useCallback((event) => {
    if (event.key === 'Enter') {
      // Need to delay for avoiding from execute searching.
      setTimeout(() => inputRef.current.blur(), 0);
    }
  }, []);

  return (
    <Tooltip title={intl.formatMessage(messages.limit)}>
      <InputBase
        inputRef={inputRef}
        value={value}
        className={classes.input}
        onChange={handleValueChange}
        onKeyDown={handleKeyDown}
        type="number"
      />
    </Tooltip>
  );
};

export default LimitCondition;
