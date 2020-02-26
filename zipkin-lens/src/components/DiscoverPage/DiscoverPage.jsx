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
import PropTypes from 'prop-types';
import React, { useEffect, useCallback } from 'react';
import { connect } from 'react-redux';
import { withRouter } from 'react-router';
import moment from 'moment';
import queryString from 'query-string';
import isEmpty from 'lodash/isEmpty';
import isEqual from 'lodash/isEqual';
import Box from '@material-ui/core/Box';
import CircularProgress from '@material-ui/core/CircularProgress';
import Paper from '@material-ui/core/Paper';
import Typography from '@material-ui/core/Typography';
import { makeStyles } from '@material-ui/styles';
import { FormattedMessage } from 'react-intl';

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
import * as tracesActionCreators from '../../actions/traces-action';
import * as servicesActionCreators from '../../actions/services-action';
import * as remoteServicesActionCreators from '../../actions/remote-services-action';
import * as spansActionCreators from '../../actions/spans-action';
import * as autocompleteKeysActionCreators from '../../actions/autocomplete-keys-action';
import * as globalSearchActionCreators from '../../actions/global-search-action';
import { globalSearchLookbackConditionPropTypes, globalSearchConditionsPropTypes } from '../../prop-types';

import ExplainBox from './ExplainBox';

import messages from './messages';

const useStyles = makeStyles(theme => ({
  header: {
    paddingRight: theme.spacing(3),
    paddingLeft: theme.spacing(3),
  },
  titleRow: {
    width: '100%',
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  upperRightBox: {
    paddingRight: theme.spacing(3),
    display: 'flex',
    alignItems: 'center',
  },
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
  loadingIndicatorWrapper: {
    width: '100%',
    height: '100%',
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
  },
  explainBoxWrapper: {
    width: '100%',
    height: '100%',
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
  },
  contentPaper: {
    height: '100%',
    marginTop: theme.spacing(2),
    marginRight: theme.spacing(3),
    marginBottom: theme.spacing(2),
    marginLeft: theme.spacing(3),
  },
  content: {
    display: 'flex',
    flexDirection: 'column',
    overflow: 'auto',
    width: '100%',
    height: '100%',
  },
}));

const propTypes = {
  isLoading: PropTypes.bool.isRequired,
  traces: PropTypes.arrayOf(PropTypes.any).isRequired,
  lastQueryParams: PropTypes.shape({}).isRequired,
  conditions: globalSearchConditionsPropTypes.isRequired,
  lookbackCondition: globalSearchLookbackConditionPropTypes.isRequired,
  limitCondition: PropTypes.number.isRequired,
  history: PropTypes.shape({ push: PropTypes.func.isRequired }).isRequired,
  location: PropTypes.shape({
    pathname: PropTypes.string.isRequired,
    search: PropTypes.string.isRequired,
  }).isRequired,
  loadTraces: PropTypes.func.isRequired,
  fetchServices: PropTypes.func.isRequired,
  fetchRemoteServices: PropTypes.func.isRequired,
  fetchSpans: PropTypes.func.isRequired,
  fetchAutocompleteKeys: PropTypes.func.isRequired,
  setConditions: PropTypes.func.isRequired,
  setLookbackCondition: PropTypes.func.isRequired,
  setLimitCondition: PropTypes.func.isRequired,
};

const DiscoverPageImpl = ({
  isLoading,
  traces,
  lastQueryParams,
  conditions,
  lookbackCondition,
  limitCondition,
  history,
  location,
  loadTraces,
  fetchServices,
  fetchRemoteServices,
  fetchSpans,
  fetchAutocompleteKeys,
  setConditions,
  setLookbackCondition,
  setLimitCondition,
}) => {
  const classes = useStyles();

  const findTraces = useCallback(() => {
    const currentTs = moment().valueOf();
    const queryParameters = buildQueryParameters(buildCommonQueryParameters(
      conditions,
      lookbackCondition,
      limitCondition,
      currentTs,
    ));
    history.push({ search: queryParameters });
    loadTraces(buildTracesApiQueryParameters(
      conditions,
      lookbackCondition,
      limitCondition,
      currentTs,
    ));
  }, [loadTraces, conditions, lookbackCondition, limitCondition, history]);

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

    setConditions(conditionsFromUrl);
    if (lookbackConditionFromUrl.value) {
      setLookbackCondition({
        value: lookbackConditionFromUrl.value,
        endTs: lookbackConditionFromUrl.endTs || moment().valueOf(),
        startTs: lookbackConditionFromUrl.startTs || moment().subtract(15, 'minutes').valueOf(),
      });
    }
    setLimitCondition(limitConditionFromUrl || 10);

    // Next, fetch data which will be shown as conditions in GlobalSearch.
    fetchServices();
    const serviceNameCondition = conditionsFromUrl.find(
      condition => condition.key === 'serviceName',
    );
    if (serviceNameCondition) {
      fetchRemoteServices(serviceNameCondition.value);
      fetchSpans(serviceNameCondition.value);
    }
    fetchAutocompleteKeys();

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
        loadTraces(apiQueryParams);
      }
    }
  });

  let content;
  if (isLoading) {
    content = (
      <Box className={classes.loadingIndicatorWrapper} data-testid="loading-indicator">
        <CircularProgress />
      </Box>
    );
  } else if (traces.length === 0) {
    content = (
      <Box className={classes.explainBoxWrapper} data-testid="explain-box">
        <ExplainBox />
      </Box>
    );
  } else {
    content = (
      <Paper className={classes.contentPaper}>
        <Box className={classes.content}>
          <TracesTab />
        </Box>
      </Paper>
    );
  }

  return (
    <>
      <Box className={classes.header}>
        <Box className={classes.titleRow}>
          <Typography variant="h5">
            <FormattedMessage {...messages.discover} />
          </Typography>
          <Box className={classes.upperRightBox}>
            <TraceJsonUploader />
            <TraceIdSearchInput />
          </Box>
        </Box>
        <GlobalSearch findData={findTraces} />
      </Box>
      {content}
    </>
  );
};

DiscoverPageImpl.propTypes = propTypes;

const mapStateToProps = state => ({
  traces: state.traces.traces,
  isLoading: state.traces.isLoading,
  lastQueryParams: state.traces.lastQueryParams,
  conditions: state.globalSearch.conditions,
  lookbackCondition: state.globalSearch.lookbackCondition,
  limitCondition: state.globalSearch.limitCondition,
});

const mapDispatchToProps = dispatch => ({
  loadTraces: params => dispatch(
    tracesActionCreators.loadTraces(params),
  ),
  fetchServices: () => dispatch(
    servicesActionCreators.fetchServices(),
  ),
  fetchRemoteServices: serviceName => dispatch(
    remoteServicesActionCreators.fetchRemoteServices(serviceName),
  ),
  fetchSpans: serviceName => dispatch(
    spansActionCreators.fetchSpans(serviceName),
  ),
  fetchAutocompleteKeys: () => dispatch(
    autocompleteKeysActionCreators.fetchAutocompleteKeys(),
  ),
  setConditions: conditions => dispatch(
    globalSearchActionCreators.setConditions(conditions),
  ),
  setLookbackCondition: lookbackCondition => dispatch(
    globalSearchActionCreators.setLookbackCondition(lookbackCondition),
  ),
  setLimitCondition: limitCondition => dispatch(
    globalSearchActionCreators.setLimitCondition(limitCondition),
  ),
});

export default connect(
  mapStateToProps,
  mapDispatchToProps,
)(withRouter(DiscoverPageImpl));
