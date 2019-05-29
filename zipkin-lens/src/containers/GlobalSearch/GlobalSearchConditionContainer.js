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

import GlobalSearchCondition from '../../components/GlobalSearch/GlobalSearchCondition';
import { fetchSpans } from '../../actions/spans-action';
import { fetchRemoteServices } from '../../actions/remote-services-action';
import { fetchAutocompleteKeys } from '../../actions/autocomplete-keys-action';
import { fetchAutocompleteValues } from '../../actions/autocomplete-values-action';
import { deleteCondition, changeConditionKey, changeConditionValue } from '../../actions/global-search-action';

const mapStateToProps = state => ({
  conditions: state.globalSearch.conditions,
  services: state.services.services,
  remoteServices: state.remoteServices.remoteServices,
  spans: state.spans.spans,
  autocompleteKeys: state.autocompleteKeys.autocompleteKeys,
  autocompleteValues: state.autocompleteValues.autocompleteValues,
});

const mapDispatchToProps = dispatch => ({
  fetchSpans: serviceName => dispatch(fetchSpans(serviceName)),
  fetchRemoteServices: serviceName => dispatch(fetchRemoteServices(serviceName)),
  fetchAutocompleteKeys: () => dispatch(fetchAutocompleteKeys()),
  fetchAutocompleteValues: autocompleteKey => dispatch(fetchAutocompleteValues(autocompleteKey)),

  changeConditionKey: (index, conditionKey) => {
    dispatch(changeConditionKey(index, conditionKey));
  },
  changeConditionValue: (index, conditionValue) => {
    dispatch(changeConditionValue(index, conditionValue));
  },
  deleteCondition: index => dispatch(deleteCondition(index)),
});

const GlobalSearchConditionContainer = connect(
  mapStateToProps,
  mapDispatchToProps,
)(GlobalSearchCondition);

export default GlobalSearchConditionContainer;
