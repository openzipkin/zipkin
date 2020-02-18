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
import React, { useMemo, useCallback } from 'react';
import { FormattedMessage } from 'react-intl';
import { useDispatch, useSelector } from 'react-redux';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { makeStyles } from '@material-ui/styles';
import Box from '@material-ui/core/Box';
import Button from '@material-ui/core/Button';

import { retrieveNextConditionKey, retrieveDefaultConditionValue } from './util';
import GlobalSearchCondition from './GlobalSearchCondition';
import { addCondition } from '../../actions/global-search-action';

import messages from './messages';

const useStyles = makeStyles({
  root: {
    width: '100%',
    display: 'flex',
    alignItems: 'center',
  },
  addButton: {
    minWidth: '2.4rem',
    width: '2.4rem',
    height: '2.4rem',
    fontSize: '1.1rem',
  },
});

const GlobalSearchConditionList = () => {
  const classes = useStyles();

  const dispatch = useDispatch();

  const conditions = useSelector(state => state.globalSearch.conditions);
  const autocompleteKeys = useSelector(state => state.autocompleteKeys.autocompleteKeys);

  const addNewCondition = useCallback(
    () => {
      const nextConditionKey = retrieveNextConditionKey(conditions, autocompleteKeys);
      dispatch(addCondition({
        key: nextConditionKey,
        value: retrieveDefaultConditionValue(nextConditionKey),
      }));
    },
    [autocompleteKeys, conditions, dispatch],
  );

  const handleAddButtonClick = useMemo(() => addNewCondition, [addNewCondition]);

  return (
    <Box
      width="100%"
      display="flex"
      alignItems="center"
      bgcolor="background.paper"
      borderRadius="0.2rem 0 0 0.2rem"
    >
      <Box
        display="flex"
        flexWrap="wrap"
        alignItems="center"
        width="100%"
        px={0.75}
        py={0.5}
      >
        {
          conditions.length === 0
            ? (
              <Box>
                <FormattedMessage {...messages.pleaseSelectCriteria} />
              </Box>
            )
            : conditions.map((condition, conditionIndex) => (
              <Box m={0.25}>
                <GlobalSearchCondition
                  conditionIndex={conditionIndex}
                  key={condition._id}
                  addCondition={addNewCondition}
                />
              </Box>
            ))
        }
        <Box m={0.25}>
          <Button
            color="primary"
            variant="contained"
            onClick={handleAddButtonClick}
            className={classes.addButton}
          >
            <FontAwesomeIcon icon={faPlus} />
          </Button>
        </Box>
      </Box>
    </Box>
  );
};

export default GlobalSearchConditionList;
