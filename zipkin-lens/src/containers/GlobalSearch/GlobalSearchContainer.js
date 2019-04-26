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
import { connect } from 'react-redux';
import { withRouter } from 'react-router';

import GlobalSearch from '../../components/GlobalSearch';
import { fetchRemoteServices } from '../../actions/remote-services-action';
import { fetchSpans } from '../../actions/spans-action';
import { fetchServices } from '../../actions/services-action';
import { fetchTraces } from '../../actions/traces-action';
import { fetchAutocompleteKeys } from '../../actions/autocomplete-keys-action';
import { fetchAutocompleteValues } from '../../actions/autocomplete-values-action';
import {
  setLookbackCondition,
  setLimitCondition,
  addCondition,
  deleteCondition,
  changeConditionKey,
  changeConditionValue,
} from '../../actions/global-search-action';

const mapStateToProps = state => ({
  services: state.services.services,
  isLoadingServices: state.services.isLoading,
  remoteServices: state.remoteServices.remoteServices,
  isLoadingRemoteServices: state.remoteServices.isLoading,
  spans: state.spans.spans,
  isLoadingSpans: state.spans.isLoading,
  conditions: state.globalSearch.conditions,
  lookbackCondition: state.globalSearch.lookbackCondition,
  limitCondition: state.globalSearch.limitCondition,
  autocompleteKeys: state.autocompleteKeys.autocompleteKeys,
  isLoadingAutocompleteKeys: state.autocompleteKeys.isLoading,
  autocompleteValues: state.autocompleteValues.autocompleteValues,
  isLoadingAutocompleteValues: state.autocompleteValues.isLoading,
});

const mapDispatchToProps = dispatch => ({
  fetchServices: () => dispatch(fetchServices()),
  fetchRemoteServices: serviceName => dispatch(fetchRemoteServices(serviceName)),
  fetchSpans: serviceName => dispatch(fetchSpans(serviceName)),
  fetchTraces: params => dispatch(fetchTraces(params)),
  fetchAutocompleteKeys: () => dispatch(fetchAutocompleteKeys()),
  fetchAutocompleteValues: autocompleteKey => dispatch(fetchAutocompleteValues(autocompleteKey)),
  setLookbackCondition: lookbackCondition => dispatch(setLookbackCondition(lookbackCondition)),
  setLimitCondition: limitCondition => dispatch(setLimitCondition(limitCondition)),
  addCondition: condition => dispatch(addCondition(condition)),
  deleteCondition: index => dispatch(deleteCondition(index)),
  changeConditionKey: (index, conditionKey) => {
    dispatch(changeConditionKey(index, conditionKey));
  },
  changeConditionValue: (index, conditionValue) => {
    dispatch(changeConditionValue(index, conditionValue));
  },
});

const GlobalSearchContainer = connect(
  mapStateToProps,
  mapDispatchToProps,
)(GlobalSearch);

export default withRouter(GlobalSearchContainer);
