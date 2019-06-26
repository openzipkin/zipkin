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
import { useSelector, useDispatch } from 'react-redux';
import { withRouter } from 'react-router';
import moment from 'moment';
import queryString from 'query-string';
import { makeStyles } from '@material-ui/styles';
import Box from '@material-ui/core/Box';
import Button from '@material-ui/core/Button';

import GlobalSearchConditionList from './GlobalSearchConditionList';
import LimitCondition from './conditions/LimitCondition';
import LookbackCondition from './conditions/LookbackCondition';
import { buildTracesQueryParameters, buildTracesApiQueryParameters, extractConditionsFromQueryParameters } from './api';
import { buildQueryParameters } from '../../util/api';
import { useMount, useUnmount } from '../../hooks';
import { addCondition, setLookbackCondition, setLimitCondition } from '../../actions/global-search-action';
import { fetchTraces } from '../../actions/traces-action';
import { fetchServices } from '../../actions/services-action';
import { fetchRemoteServices } from '../../actions/remote-services-action';
import { fetchSpans } from '../../actions/spans-action';
import { fetchAutocompleteKeys } from '../../actions/autocomplete-keys-action';

const useStyles = makeStyles({
  findButton: {
    minWidth: '2.8rem',
    width: '2.8rem',
    height: '100%',
    fontSize: '1.2rem',
    borderRadius: '0 0.2rem 0.2rem 0',
    boxShadow: 'none',
  },
});

const propTypes = {
  history: PropTypes.shape({ push: PropTypes.func.isRequired }).isRequired,
  location: PropTypes.shape({ search: PropTypes.string.isRequired }).isRequired,
};

const GlobalSearch = ({ history, location }) => {
  const classes = useStyles();

  const dispatch = useDispatch();

  const conditions = useSelector(state => state.globalSearch.conditions);
  const lookbackCondition = useSelector(state => state.globalSearch.lookbackCondition);
  const limitCondition = useSelector(state => state.globalSearch.limitCondition);

  const findTraces = () => {
    const queryParameters = buildQueryParameters(buildTracesQueryParameters(
      conditions,
      lookbackCondition,
      limitCondition,
    ));
    const loc = { pathname: '/zipkin', search: queryParameters };
    history.push(loc);
    dispatch(fetchTraces(buildTracesApiQueryParameters(
      conditions,
      lookbackCondition,
      limitCondition,
    )));
  };

  const handleFindButtonClick = findTraces;

  const handleKeyDown = (event) => {
    if (document.activeElement.tagName === 'BODY' && event.key === 'Enter') {
      findTraces();
    }
  };

  useMount(() => {
    window.addEventListener('keydown', handleKeyDown);

    const queryParams = queryString.parse(location.search);
    const {
      conditions: conditionsFromUrl,
      lookbackCondition: lookbackConditionFromUrl,
      limitCondition: limitConditionFromUrl,
    } = extractConditionsFromQueryParameters(queryParams);

    conditionsFromUrl.forEach(condition => dispatch(addCondition(condition)));
    dispatch(setLookbackCondition({
      value: lookbackConditionFromUrl.value || '1h',
      endTs: lookbackConditionFromUrl.endTs || moment().valueOf(),
      startTs: lookbackConditionFromUrl.startTs || moment().subtract(1, 'hours').valueOf(),
    }));
    dispatch(setLimitCondition(limitConditionFromUrl || 10));

    dispatch(fetchServices());
    const serviceNameCondition = conditionsFromUrl.find(
      condition => condition.key === 'serviceName',
    );
    if (serviceNameCondition) {
      dispatch(fetchRemoteServices(serviceNameCondition.value));
      dispatch(fetchSpans(serviceNameCondition.value));
    }
    dispatch(fetchAutocompleteKeys());
    dispatch(fetchTraces(buildTracesApiQueryParameters(
      conditionsFromUrl,
      lookbackConditionFromUrl,
      limitConditionFromUrl,
    )));
  });

  useUnmount(() => document.removeEventListener('keydown', handleKeyDown));

  return (
    <Box
      display="flex"
      width="100%"
      minHeight="3.8rem"
      maxHeight="10rem"
      boxShadow={3}
      borderRadius="0.2rem"
    >
      <Box display="flex" width="100%">
        <GlobalSearchConditionList />
      </Box>
      <LimitCondition />
      <LookbackCondition />
      <Box
        display="flex"
        alignItems="center"
        minHeight="100%"
        maxheight="10rem"
      >
        <Button
          color="primary"
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
