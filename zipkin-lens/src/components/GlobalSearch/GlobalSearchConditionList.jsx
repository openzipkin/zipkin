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
import { makeStyles } from '@material-ui/styles';
import Box from '@material-ui/core/Box';
import Button from '@material-ui/core/Button';

import { retrieveNextConditionKey, retrieveDefaultConditionValue } from './util';
import GlobalSearchCondition from './GlobalSearchCondition';
import { globalSearchConditionsPropTypes } from '../../prop-types';
import * as globalSearchActionCreators from '../../actions/global-search-action';

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

const propTypes = {
  conditions: globalSearchConditionsPropTypes.isRequired,
  addCondition: PropTypes.func.isRequired,
  autocompleteKeys: PropTypes.arrayOf(PropTypes.string).isRequired,
};

const GlobalSearchConditionList = ({ conditions, addCondition, autocompleteKeys }) => {
  const classes = useStyles();

  const handleAddButtonClick = () => {
    const nextConditionKey = retrieveNextConditionKey(conditions, autocompleteKeys);
    addCondition({
      key: nextConditionKey,
      value: retrieveDefaultConditionValue(nextConditionKey),
    });
  };

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
                Please select the criteria for your trace lookup
              </Box>
            )
            : conditions.map((condition, conditionIndex) => (
              <Box m={0.25}>
                <GlobalSearchCondition
                  conditionIndex={conditionIndex}
                  key={condition._id}
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
            <Box component="span" className="fas fa-plus" />
          </Button>
        </Box>
      </Box>
    </Box>
  );
};

GlobalSearchConditionList.propTypes = propTypes;

const mapStateToProps = state => ({
  conditions: state.globalSearch.conditions,
  autocompleteKeys: state.autocompleteKeys.autocompleteKeys,
});

const mapDispatchToProps = (dispatch) => {
  const { addCondition } = globalSearchActionCreators;

  return {
    addCondition: condition => dispatch(addCondition(condition)),
  };
};

export default connect(
  mapStateToProps,
  mapDispatchToProps,
)(GlobalSearchConditionList);
