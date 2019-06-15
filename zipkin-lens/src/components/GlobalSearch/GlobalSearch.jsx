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
import { withRouter } from 'react-router';
import { makeStyles } from '@material-ui/styles';
import Box from '@material-ui/core/Box';
import Button from '@material-ui/core/Button';

import GlobalSearchConditionListContainer from '../../containers/GlobalSearch/GlobalSearchConditionListContainer';
import { buildTracesQueryParameters, buildTracesApiQueryParameters } from './api';
import { globalSearchConditionsPropTypes, globalSearchLookbackConditionPropTypes } from '../../prop-types';

const useStyles = makeStyles({
  findButton: {
    minWidth: '2.8rem',
    width: '2.8rem',
    height: '100%',
    fontSize: '1.2rem',
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
    >
      <Box display="flex" width="100%">
        <GlobalSearchConditionListContainer />
      </Box>
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

export default withRouter(GlobalSearch);
