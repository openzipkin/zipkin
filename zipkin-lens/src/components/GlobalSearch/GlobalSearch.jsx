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
import React, { useEffect } from 'react';
import { connect } from 'react-redux';
import { withRouter } from 'react-router';
import { makeStyles } from '@material-ui/styles';
import Box from '@material-ui/core/Box';
import Button from '@material-ui/core/Button';

import GlobalSearchConditionList from './GlobalSearchConditionList';
import LookbackCondition from './conditions/LookbackCondition';
import { buildTracesQueryParameters, buildTracesApiQueryParameters } from './api';
import { globalSearchConditionsPropTypes, globalSearchLookbackConditionPropTypes } from '../../prop-types';
import * as tracesActionCreators from '../../actions/traces-action';
import * as servicesActionCreators from '../../actions/services-action';

const useStyles = makeStyles({
  findButton: {
    minWidth: '2.8rem',
    width: '2.8rem',
    height: '100%',
    fontSize: '1.2rem',
    borderRadius: '0 0.5rem 0.5rem 0',
    boxShadow: 'none',
  },
});

const propTypes = {
  history: PropTypes.shape({ push: PropTypes.func.isRequired }).isRequired,
  conditions: globalSearchConditionsPropTypes.isRequired,
  lookbackCondition: globalSearchLookbackConditionPropTypes.isRequired,
  limitCondition: PropTypes.number.isRequired,
  fetchTraces: PropTypes.func.isRequired,
  fetchServices: PropTypes.func.isRequired,
};

const GlobalSearch = ({
  history,
  conditions,
  lookbackCondition,
  limitCondition,
  fetchTraces,
  fetchServices,
}) => {
  const classes = useStyles();

  const handleFindButtonClick = () => {
    const queryParameters = buildTracesQueryParameters(
      conditions,
      lookbackCondition,
      limitCondition,
    );
    const location = { pathname: '/zipkin', search: queryParameters };
    history.push(location);

    fetchTraces(buildTracesApiQueryParameters(
      conditions,
      lookbackCondition,
      limitCondition,
    ));
  };

  useEffect(() => {
    fetchServices();
  }, []);

  return (
    <Box
      display="flex"
      width="100%"
      minHeight="3.8rem"
      maxHeight="10rem"
      boxShadow={1}
      borderRadius="0.5rem"
    >
      <Box display="flex" width="100%">
        <GlobalSearchConditionList />
      </Box>
      <LookbackCondition />
      <Box
        display="flex"
        alignItems="center"
        minHeight="100%"
        maxheight="10rem"
      >
        <Button
          color="secondary"
          variant="contained"
          onClick={handleFindButtonClick}
          className={classes.findButton}
        >
          <Box component="span" className="fas fa-search" />
        </Button>
      </Box>
    </Box>
  );
};

GlobalSearch.propTypes = propTypes;

const mapStateToProps = state => ({
  conditions: state.globalSearch.conditions,
  lookbackCondition: state.globalSearch.lookbackCondition,
  limitCondition: state.globalSearch.limitCondition,
});

const mapDispatchToProps = (dispatch) => {
  const { fetchTraces } = tracesActionCreators;
  const { fetchServices } = servicesActionCreators;

  return {
    fetchTraces: params => dispatch(fetchTraces(params)),
    fetchServices: () => dispatch(fetchServices()),
  };
};

export default connect(
  mapStateToProps,
  mapDispatchToProps,
)(withRouter(GlobalSearch));
