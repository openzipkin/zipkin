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
import React, { useState, useRef, useCallback } from 'react';
import { useSelector, useDispatch } from 'react-redux';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { makeStyles } from '@material-ui/styles';
import Button from '@material-ui/core/Button';
import Paper from '@material-ui/core/Paper';

import GlobalSearchConditionKey from './GlobalSearchConditionKey';
import GlobalSearchConditionValue from './GlobalSearchConditionValue';
import { deleteCondition } from '../../actions/global-search-action';

const useStyles = makeStyles(theme => ({
  root: {
    display: 'flex',
    alignItems: 'center',
    height: '2.4rem',
    // I want to set `overflow:hidden` here, but I cannot do that because ReactSelect
    // opens menu as a child component.
    // ReactSelect supports menu component using Portal too, but I don't want to use
    // it because there is a bug of display when `control` component is moved for layout.
  },
  deleteButton: {
    minWidth: '2.4rem',
    width: '2.4rem',
    height: '100%',
    fontSize: '1.2rem',
    boxShadow: 'none',
    borderTopLeftRadius: 0,
    borderTopRightRadius: '0.2rem',
    borderBottomLeftRadius: 0,
    borderBottomRightRadius: '0.2rem',
    color: theme.palette.primary.contrastText,
    backgroundColor: theme.palette.primary.light,
    '&:hover': {
      backgroundColor: theme.palette.primary.main,
    },
  },
}));

const propTypes = {
  conditionIndex: PropTypes.number.isRequired,
  addCondition: PropTypes.func.isRequired,
};

const GlobalSearchCondition = ({ conditionIndex, addCondition }) => {
  const classes = useStyles();

  const dispatch = useDispatch();

  const conditions = useSelector(state => state.globalSearch.conditions);

  const [isKeyFocused, setIsKeyFocused] = useState(false);
  const [isValueFocused, setIsValueFocused] = useState(false);

  // These ref are needed for using a new data in setTimeout of
  // deleteWhenValueIsEmpty function.
  // Please see: https://reactjs.org/docs/hooks-faq.html#why-am-i-seeing-stale-props-or-state-inside-my-function
  const conditionsRef = useRef(conditions);
  const isKeyFocusedRef = useRef(isKeyFocused);
  const isValueFocusedRef = useRef(isValueFocused);

  conditionsRef.current = conditions;
  isKeyFocusedRef.current = isKeyFocused;
  isValueFocusedRef.current = isValueFocused;

  const deleteWhenValueIsEmpty = useCallback(
    () => {
      setTimeout(() => {
        if (
          !isKeyFocusedRef.current
          && !isValueFocusedRef.current
          && !conditionsRef.current[conditionIndex].value
        ) {
          dispatch(deleteCondition(conditionIndex));
        }
      }, 0);
    },
    [conditionIndex, dispatch],
  );

  const handleKeyFocus = useCallback(() => setIsKeyFocused(true), []);

  const handleKeyBlur = useCallback(
    () => {
      setIsKeyFocused(false);
      // If the user blurs with en empty value, delete this condition component.
      // This behavior improves usability,
      deleteWhenValueIsEmpty();
    },
    [deleteWhenValueIsEmpty],
  );

  const handleValueFocus = useCallback(() => setIsValueFocused(true), []);

  const handleValueBlur = useCallback(
    () => {
      setIsValueFocused(false);
      // If the user blurs with en empty value, delete this condition component.
      // This behavior improves usability,
      deleteWhenValueIsEmpty();
    },
    [deleteWhenValueIsEmpty],
  );

  const handleDeleteButtonClick = useCallback(
    () => {
      dispatch(deleteCondition(conditionIndex));
    },
    [conditionIndex, dispatch],
  );

  const valueRef = useRef(null);
  const focusValue = useCallback(
    () => {
      // Delay is needed to avoid calling focus
      // until the value element is mounted.
      // If don't delay, focus cannot be executed.
      setTimeout(() => valueRef.current.focus(), 0);
    },
    [],
  );

  return (
    <Paper className={classes.root}>
      <GlobalSearchConditionKey
        conditionIndex={conditionIndex}
        isFocused={isKeyFocused}
        onFocus={handleKeyFocus}
        onBlur={handleKeyBlur}
        focusValue={focusValue}
      />
      <GlobalSearchConditionValue
        conditionIndex={conditionIndex}
        isFocused={isValueFocused}
        onFocus={handleValueFocus}
        onBlur={handleValueBlur}
        valueRef={valueRef}
        addCondition={addCondition}
      />
      <Button
        variant="contained"
        onClick={handleDeleteButtonClick}
        className={classes.deleteButton}
      >
        <FontAwesomeIcon icon={faTimes} />
      </Button>
    </Paper>
  );
};

GlobalSearchCondition.propTypes = propTypes;

export default GlobalSearchCondition;
