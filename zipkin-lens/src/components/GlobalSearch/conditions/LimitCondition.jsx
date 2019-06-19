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
import InputBase from '@material-ui/core/InputBase';

import * as globalSearchActionCreators from '../../../actions/global-search-action';

const useStyles = makeStyles(theme => ({
  input: {
    width: '6rem',
    padding: '0 0.5rem',
    color: theme.palette.secondary.contrastText,
    backgroundColor: theme.palette.secondary.main,
    '&:hover': {
      backgroundColor: theme.palette.secondary.dark,
    },
    '&:focus-within': {
      backgroundColor: theme.palette.secondary.dark,
    },
  },
}));

const propTypes = {
  limitCondition: PropTypes.number.isRequired,
  onChange: PropTypes.func.isRequired,
};

const LimitCondition = ({
  limitCondition,
  onChange,
}) => {
  const classes = useStyles();

  const handleValueChange = (event) => {
    onChange(event.target.value);
  };

  return (
    <InputBase
      value={limitCondition}
      className={classes.input}
      onChange={handleValueChange}
      type="number"
    />
  );
};

LimitCondition.propTypes = propTypes;

const mapStateToProps = state => ({
  limitCondition: state.globalSearch.limitCondition,
});

const mapDispatchToProps = (dispatch) => {
  const { setLimitCondition } = globalSearchActionCreators;
  return {
    onChange: limitCondition => dispatch(setLimitCondition(limitCondition)),
  };
};

export default connect(
  mapStateToProps,
  mapDispatchToProps,
)(LimitCondition);
