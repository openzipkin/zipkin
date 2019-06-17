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
import React, { useState } from 'react';
import { makeStyles } from '@material-ui/styles';
import Box from '@material-ui/core/Box';
import Button from '@material-ui/core/Button';
import Paper from '@material-ui/core/Paper';

import GlobalSearchConditionKey from './GlobalSearchConditionKey';
import GlobalSearchConditionValue from './GlobalSearchConditionValue';

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
  deleteCondition: PropTypes.func.isRequired,
};

const GlobalSearchCondition = ({
  conditionIndex,
  deleteCondition,
}) => {
  const classes = useStyles();

  const [isKeyFocused, setIsKeyFocused] = useState(false);
  const [isValueFocused, setIsValueFocused] = useState(false);

  const handleKeyFocus = () => setIsKeyFocused(true);
  const handleKeyBlur = () => setIsKeyFocused(false);
  const handleValueFocus = () => setIsValueFocused(true);
  const handleValueBlur = () => setIsValueFocused(false);

  const handleDeleteButtonClick = () => {
    deleteCondition(conditionIndex);
  };

  return (
    <Paper className={classes.root}>
      <GlobalSearchConditionKey
        conditionIndex={conditionIndex}
        isFocused={isKeyFocused}
        onFocus={handleKeyFocus}
        onBlur={handleKeyBlur}
      />
      <GlobalSearchConditionValue
        conditionIndex={conditionIndex}
        isFocused={isValueFocused}
        onFocus={handleValueFocus}
        onBlur={handleValueBlur}
      />
      <Button
        variant="contained"
        onClick={handleDeleteButtonClick}
        className={classes.deleteButton}
      >
        <Box component="span" className="fas fa-times" />
      </Button>
    </Paper>
  );
};

GlobalSearchCondition.propTypes = propTypes;

export default GlobalSearchCondition;
