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
import React, { useEffect } from 'react';
import { connect } from 'react-redux';
import { withRouter } from 'react-router';
import moment from 'moment';
import queryString from 'query-string';
import { makeStyles } from '@material-ui/styles';
import Box from '@material-ui/core/Box';
import Button from '@material-ui/core/Button';

import GlobalSearchConditionList from './GlobalSearchConditionList';
import LimitCondition from './conditions/LimitCondition';
import LookbackCondition from './conditions/LookbackCondition';
import { buildTracesQueryParameters, buildTracesApiQueryParameters } from './api';
import { extractConditionsFromQueryParameters } from './util';
import { globalSearchConditionsPropTypes, globalSearchLookbackConditionPropTypes } from '../../prop-types';
import * as globalSearchActionCreators from '../../actions/global-search-action';
import * as tracesActionCreators from '../../actions/traces-action';
import * as servicesActionCreators from '../../actions/services-action';
import * as remoteServicesActionCreators from '../../actions/remote-services-action';
import * as spansActionCreators from '../../actions/spans-action';
import * as autocompleteKeysActionCreators from '../../actions/autocomplete-keys-action';

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
  location: PropTypes.shape({ search: PropTypes.string.isRequired }).isRequired,
  conditions: globalSearchConditionsPropTypes.isRequired,
  lookbackCondition: globalSearchLookbackConditionPropTypes.isRequired,
  limitCondition: PropTypes.number.isRequired,
  addCondition: PropTypes.func.isRequired,
  setLookbackCondition: PropTypes.func.isRequired,
  setLimitCondition: PropTypes.func.isRequired,
  fetchTraces: PropTypes.func.isRequired,
  fetchServices: PropTypes.func.isRequired,
  fetchRemoteServices: PropTypes.func.isRequired,
  fetchSpans: PropTypes.func.isRequired,
  fetchAutocompleteKeys: PropTypes.func.isRequired,
};

const GlobalSearch = ({
  history,
  location,
  conditions,
  lookbackCondition,
  limitCondition,
  addCondition,
  setLookbackCondition,
  setLimitCondition,
  fetchTraces,
  fetchServices,
  fetchRemoteServices,
  fetchSpans,
  fetchAutocompleteKeys,
}) => {
  const classes = useStyles();

  const handleFindButtonClick = () => {
    const queryParameters = buildTracesQueryParameters(
      conditions,
      lookbackCondition,
      limitCondition,
    );
    const loc = { pathname: '/zipkin', search: queryParameters };
    history.push(loc);
    fetchTraces(buildTracesApiQueryParameters(
      conditions,
      lookbackCondition,
      limitCondition,
    ));
  };

  useEffect(() => {
    const queryParams = queryString.parse(location.search);
    const {
      conditions: conditionsFromUrl,
      lookbackCondition: lookbackConditionFromUrl,
      limitCondition: limitConditionFromUrl,
    } = extractConditionsFromQueryParameters(queryParams);
    conditionsFromUrl.forEach(condition => addCondition(condition));
    setLookbackCondition({
      value: lookbackCondition.value || '1h',
      endTs: lookbackCondition.endTs || moment().valueOf(),
      startTs: lookbackCondition.startTs || moment().subtract(1, 'hours').valueOf(),
    });
    setLimitCondition(limitCondition || 10);

    fetchServices();
    const serviceNameCondition = conditionsFromUrl.find(
      condition => condition.key === 'serviceName',
    );
    if (serviceNameCondition) {
      fetchRemoteServices(serviceNameCondition.value);
      fetchSpans(serviceNameCondition.value);
    }
    fetchAutocompleteKeys();
    fetchTraces(buildTracesApiQueryParameters(
      conditionsFromUrl,
      lookbackConditionFromUrl,
      limitConditionFromUrl,
    ));
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
      <LimitCondition />
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
  const { fetchRemoteServices } = remoteServicesActionCreators;
  const { fetchSpans } = spansActionCreators;
  const { fetchAutocompleteKeys } = autocompleteKeysActionCreators;
  const { addCondition, setLookbackCondition, setLimitCondition } = globalSearchActionCreators;

  return {
    addCondition: condition => dispatch(addCondition(condition)),
    setLookbackCondition: lookbackCondition => dispatch(setLookbackCondition(lookbackCondition)),
    setLimitCondition: limitCondition => dispatch(setLimitCondition(limitCondition)),
    fetchTraces: params => dispatch(fetchTraces(params)),
    fetchServices: () => dispatch(fetchServices()),
    fetchRemoteServices: serviceName => dispatch(fetchRemoteServices(serviceName)),
    fetchSpans: serviceName => dispatch(fetchSpans(serviceName)),
    fetchAutocompleteKeys: () => dispatch(fetchAutocompleteKeys),
  };
};

export default connect(
  mapStateToProps,
  mapDispatchToProps,
)(withRouter(GlobalSearch));
