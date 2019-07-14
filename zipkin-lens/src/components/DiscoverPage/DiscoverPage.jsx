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
import _ from 'lodash';
import { AutoSizer } from 'react-virtualized';
import { makeStyles } from '@material-ui/styles';
import AppBar from '@material-ui/core/AppBar';
import Box from '@material-ui/core/Box';
import Paper from '@material-ui/core/Paper';
import Tab from '@material-ui/core/Tab';
import Tabs from '@material-ui/core/Tabs';
import Typography from '@material-ui/core/Typography';

import TraceJsonUploader from './TraceJsonUploader';
import TraceIdSearchInput from './TraceIdSearchInput';
import TracesTab from '../../containers/Browser/BrowserContainer';
import DependenciesTab from '../../containers/Dependencies/DependenciesContainer';
import GlobalSearch from '../GlobalSearch';
import {
  buildCommonQueryParameters,
  buildTracesApiQueryParameters,
  buildDependenciesApiQueryParameters,
  extractConditionsFromQueryParameters,
} from './api';
import { buildQueryParameters } from '../../util/api';
import { useMount } from '../../hooks';
import { fetchTraces } from '../../actions/traces-action';
import { fetchServices } from '../../actions/services-action';
import { fetchRemoteServices } from '../../actions/remote-services-action';
import { fetchSpans } from '../../actions/spans-action';
import { fetchAutocompleteKeys } from '../../actions/autocomplete-keys-action';
import { fetchDependencies } from '../../actions/dependencies-action';
import { addCondition, setLookbackCondition, setLimitCondition } from '../../actions/global-search-action';

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
    flex: '0 1 100%',
    marginTop: '1.5rem',
    marginBottom: '1rem',
    overflow: 'auto',
  },
}));

const tracesTab = 0;
const dependenciesTab = 1;

const DiscoverPage = ({ history, location }) => {
  const classes = useStyles();

  const dispatch = useDispatch();

  const conditions = useSelector(state => state.globalSearch.conditions);
  const lookbackCondition = useSelector(state => state.globalSearch.lookbackCondition);
  const limitCondition = useSelector(state => state.globalSearch.limitCondition);

  const findTraces = useCallback(() => {
    const currentTime = moment();

    const queryParameters = buildQueryParameters(buildCommonQueryParameters(
      conditions,
      lookbackCondition,
      limitCondition,
      currentTime,
    ));
    history.push({ pathname: '/zipkin', search: queryParameters });

    dispatch(fetchTraces(buildTracesApiQueryParameters(
      conditions,
      lookbackCondition,
      limitCondition,
      currentTime,
    )));
  }, [conditions, lookbackCondition, limitCondition, dispatch, history]);

  const findDependencies = useCallback(() => {
    const currentTime = moment();

    const queryParameters = buildQueryParameters(buildCommonQueryParameters(
      conditions,
      lookbackCondition,
      limitCondition,
      currentTime,
    ));
    history.push({ pathname: '/zipkin/dependency', search: queryParameters });

    dispatch(fetchDependencies(buildDependenciesApiQueryParameters(
      lookbackCondition,
      currentTime,
    )));
  }, [conditions, lookbackCondition, limitCondition, dispatch, history]);

  const [tabValue, setTabValue] = React.useState(tracesTab);

  const findData = useCallback(() => {
    switch (tabValue) {
      case tracesTab:
        findTraces();
        break;
      case dependenciesTab:
        findDependencies();
        break;
      default:
    }
  }, [findDependencies, findTraces, tabValue]);

  const handleTabChange = useCallback((event, newTabValue) => {
    setTabValue(newTabValue);

    // If there are not any query parameters in the address bar, don't
    // fetch any data.
    if (location.search) {
      switch (newTabValue) {
        case tracesTab:
          // Fetch traces only if one or more conditions are set.
          if (!_.isEmpty(conditions)
            || !_.isEmpty(lookbackCondition)
            || !!limitCondition
          ) {
            findTraces();
          }
          break;
        case dependenciesTab:
          if (!_.isEmpty(conditions) || !_.isEmpty(lookbackCondition)) {
            findDependencies();
          }
          break;
        default:
          // Do nothing
      }
    } else {
      switch (newTabValue) {
        case tracesTab:
          history.push({ pathname: '/zipkin' });
          break;
        case dependenciesTab:
          history.push({ pathname: '/zipkin/dependency' });
          break;
        default:
          // Do nothing
      }
    }
  }, [
    findTraces,
    findDependencies,
    conditions,
    lookbackCondition,
    limitCondition,
    location.search,
    history,
  ]);

  const handleKeyDown = useCallback((event) => {
    if (document.activeElement.tagName === 'BODY' && event.key === 'Enter') {
      findData();
    }
  }, [findData]);

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

    conditionsFromUrl.forEach(condition => dispatch(addCondition(condition)));
    dispatch(setLookbackCondition({
      value: lookbackConditionFromUrl.value || '1h',
      endTs: lookbackConditionFromUrl.endTs || moment().valueOf(),
      startTs: lookbackConditionFromUrl.startTs || moment().subtract(1, 'hours').valueOf(),
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

    const currentTime = moment();

    // Finally fetch traces-data or dependencies-data according to location.pathname.
    switch (location.pathname) {
      case '/zipkin':
      case '/zipkin/':
        setTabValue(tracesTab);
        // Fetch traces only if one or more conditions are set.
        if (!_.isEmpty(conditionsFromUrl)
          || !_.isEmpty(lookbackConditionFromUrl)
          || !!limitConditionFromUrl
        ) {
          dispatch(fetchTraces(buildTracesApiQueryParameters(
            conditionsFromUrl,
            lookbackConditionFromUrl,
            limitConditionFromUrl,
            currentTime,
          )));
        }
        break;
      case '/zipkin/dependency':
        setTabValue(dependenciesTab);
        if (!_.isEmpty(conditionsFromUrl) || !_.isEmpty(lookbackConditionFromUrl)) {
          dispatch(fetchDependencies(buildDependenciesApiQueryParameters(
            lookbackConditionFromUrl,
            currentTime,
          )));
        }
        break;
      default:
        setTabValue(-1);
        break;
    }
  });

  return (
    <React.Fragment>
      <Box width="100%" display="flex" justifyContent="space-between">
        <Box display="flex" alignItems="center">
          <Typography variant="h5">
            Discover
          </Typography>
        </Box>
        <Box pr={4} display="flex" alignItems="center">
          <TraceJsonUploader />
          <TraceIdSearchInput />
        </Box>
      </Box>
      <GlobalSearch findData={findData} />
      <Paper className={classes.contentPaper}>
        <Box overflow="auto" width="100%" height="100%">
          <AppBar position="static">
            <Tabs
              value={tabValue}
              onChange={handleTabChange}
              className={classes.tabs}
            >
              <Tab label="Traces" className={classes.tab} />
              <Tab label="Dependencies" className={classes.tab} />
            </Tabs>
          </AppBar>
          { /* 2rem is the height of the Appbar. */}
          <Box height="calc(100% - 2rem)">
            <AutoSizer>
              {
                ({ height, width }) => (
                  <Box
                    height={height}
                    width={width}
                    overflow="auto"
                  >
                    {tabValue === tracesTab && <TracesTab />}
                    {tabValue === dependenciesTab && <DependenciesTab />}
                  </Box>
                )
              }
            </AutoSizer>
          </Box>
        </Box>
      </Paper>
    </React.Fragment>
  );
};

DiscoverPage.propTypes = propTypes;

export default withRouter(DiscoverPage);
