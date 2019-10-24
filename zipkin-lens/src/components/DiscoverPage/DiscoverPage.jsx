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
import React, { useEffect, useCallback } from 'react';
import { useSelector, useDispatch } from 'react-redux';
import { withRouter } from 'react-router';
import moment from 'moment';
import queryString from 'query-string';
import isEmpty from 'lodash/isEmpty';
import isEqual from 'lodash/isEqual';
import { makeStyles } from '@material-ui/styles';
import Box from '@material-ui/core/Box';
import Paper from '@material-ui/core/Paper';
import Typography from '@material-ui/core/Typography';

import TraceJsonUploader from '../Common/TraceJsonUploader';
import TraceIdSearchInput from '../Common/TraceIdSearchInput';
import TracesTab from './TracesTab';
import GlobalSearch from '../GlobalSearch';
import {
  buildCommonQueryParameters,
  buildTracesApiQueryParameters,
  extractConditionsFromQueryParameters,
} from './api';
import { buildQueryParameters } from '../../util/api';
import { useMount } from '../../hooks';
import { loadTraces } from '../../actions/traces-action';
import { fetchServices } from '../../actions/services-action';
import { fetchRemoteServices } from '../../actions/remote-services-action';
import { fetchSpans } from '../../actions/spans-action';
import { fetchAutocompleteKeys } from '../../actions/autocomplete-keys-action';
import { setConditions, setLookbackCondition, setLimitCondition } from '../../actions/global-search-action';

const propTypes = {
  history: PropTypes.shape({ push: PropTypes.func.isRequired }).isRequired,
  location: PropTypes.shape({
    pathname: PropTypes.string.isRequired,
    search: PropTypes.string.isRequired,
  }).isRequired,
};

const useStyles = makeStyles(theme => ({
  tabs: {
    color: theme.palette.text.primary,
    backgroundColor: theme.palette.common.white,
    height: '2rem',
    minHeight: '2rem',
  },
  tab: {
    height: '2rem',
    minHeight: '2rem',
  },
  contentPaper: {
    width: '100%',
    height: '100%',
  },
}));

const DiscoverPage = ({ history, location }) => {
  const classes = useStyles();
  const dispatch = useDispatch();
  const lastQueryParams = useSelector(state => state.traces.lastQueryParams);
  const conditions = useSelector(state => state.globalSearch.conditions);
  const lookbackCondition = useSelector(state => state.globalSearch.lookbackCondition);
  const limitCondition = useSelector(state => state.globalSearch.limitCondition);

  const findTraces = useCallback(() => {
    const currentTs = moment().valueOf();
    const queryParameters = buildQueryParameters(buildCommonQueryParameters(
      conditions,
      lookbackCondition,
      limitCondition,
      currentTs,
    ));
    history.push({ pathname: '/zipkin', search: queryParameters });
    dispatch(loadTraces(buildTracesApiQueryParameters(
      conditions,
      lookbackCondition,
      limitCondition,
      currentTs,
    )));
  }, [conditions, lookbackCondition, limitCondition, dispatch, history]);

  const handleKeyDown = useCallback((event) => {
    if (document.activeElement.tagName === 'BODY' && event.key === 'Enter') {
      findTraces();
    }
  }, [findTraces]);

  useEffect(() => {
    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [handleKeyDown]);

  useMount(() => {
    // When mounted, parse a query parameter of a browser's address bar
    // and retrieve initial conditions, and setup these conditions as redux state.
    const queryParams = queryString.parse(location.search);
    const {
      conditions: conditionsFromUrl,
      lookbackCondition: lookbackConditionFromUrl,
      limitCondition: limitConditionFromUrl,
    } = extractConditionsFromQueryParameters(queryParams);

    dispatch(setConditions(conditionsFromUrl));
    dispatch(setLookbackCondition({
      value: lookbackConditionFromUrl.value || '15m',
      endTs: lookbackConditionFromUrl.endTs || moment().valueOf(),
      startTs: lookbackConditionFromUrl.startTs || moment().subtract(15, 'minutes').valueOf(),
    }));
    dispatch(setLimitCondition(limitConditionFromUrl || 10));

    // Next, fetch data which will be shown as conditions in GlobalSearch.
    dispatch(fetchServices());
    const serviceNameCondition = conditionsFromUrl.find(
      condition => condition.key === 'serviceName',
    );
    if (serviceNameCondition) {
      dispatch(fetchRemoteServices(serviceNameCondition.value));
      dispatch(fetchSpans(serviceNameCondition.value));
    }
    dispatch(fetchAutocompleteKeys());

    const currentTs = lookbackConditionFromUrl.endTs || moment().valueOf();

    // Fetch traces only if one or more conditions are set.
    if (!isEmpty(conditionsFromUrl)
      || !isEmpty(lookbackConditionFromUrl)
      || !!limitConditionFromUrl
    ) {
      const apiQueryParams = buildTracesApiQueryParameters(
        conditionsFromUrl,
        lookbackConditionFromUrl,
        limitConditionFromUrl,
        currentTs,
      );
      if (!isEqual(apiQueryParams, lastQueryParams)) {
        dispatch(loadTraces(apiQueryParams));
      }
    }
  });

  return (
    <>
      <Box pl={3} pr={3}>
        <Box width="100%" display="flex" justifyContent="space-between">
          <Box display="flex" alignItems="center">
            <Typography variant="h5">
              Discover
            </Typography>
          </Box>
          <Box pr={3} display="flex" alignItems="center">
            <TraceJsonUploader />
            <TraceIdSearchInput />
          </Box>
        </Box>
        <GlobalSearch findData={findTraces} />
      </Box>
      <Box
        flex="0 1 100%"
        marginTop={1.5}
        marginBottom={1}
        marginRight={3}
        marginLeft={3}
      >
        <Paper className={classes.contentPaper}>
          <Box display="flex" flexDirection="column" overflow="auto" width="100%" height="100%">
            <TracesTab />
          </Box>
        </Paper>
      </Box>
    </>
  );
};

DiscoverPage.propTypes = propTypes;

export default withRouter(DiscoverPage);
