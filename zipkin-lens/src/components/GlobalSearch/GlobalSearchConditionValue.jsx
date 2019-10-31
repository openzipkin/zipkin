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
import React, { useCallback } from 'react';
import { useSelector, useDispatch } from 'react-redux';

import DurationCondition from './conditions/DurationCondition';
import NameCondition from './conditions/NameCondition';
import TagCondition from './conditions/TagCondition';
import { changeConditionValue } from '../../actions/global-search-action';
import { fetchSpans } from '../../actions/spans-action';
import { fetchRemoteServices } from '../../actions/remote-services-action';

const propTypes = {
  conditionIndex: PropTypes.number.isRequired,
  valueRef: PropTypes.shape({}).isRequired,
  addCondition: PropTypes.func.isRequired,
  isFocused: PropTypes.bool.isRequired,
  onFocus: PropTypes.func.isRequired,
  onBlur: PropTypes.func.isRequired,
};

const GlobalSearchConditionValue = ({
  conditionIndex,
  valueRef,
  addCondition,
  isFocused,
  onFocus,
  onBlur,
}) => {
  const dispatch = useDispatch();
  const {
    services,
    isLoading: isLoadingServices,
  } = useSelector(state => state.services);
  const {
    remoteServices,
    isLoading: isLoadingRemoteServices,
  } = useSelector(state => state.remoteServices);
  const {
    spans,
    isLoading: isLoadingSpans,
  } = useSelector(state => state.spans);
  const {
    autocompleteValues,
    isLoading: isLoadingAutocompleteValues,
  } = useSelector(state => state.autocompleteValues);
  const conditions = useSelector(state => state.globalSearch.conditions);

  const { key: conditionKey, value: conditionValue } = conditions[conditionIndex];

  const handleValueChange = useCallback((value) => {
    dispatch(changeConditionValue(conditionIndex, value));
    if (conditionKey === 'serviceName') {
      dispatch(fetchRemoteServices(value));
      dispatch(fetchSpans(value));
    }
  }, [conditionIndex, conditionKey, dispatch]);

  const commonProps = {
    value: conditionValue,
    onChange: handleValueChange,
    valueRef,
    addCondition,
    isFocused,
    onFocus,
    onBlur,
  };

  switch (conditionKey) {
    case 'serviceName':
    case 'remoteServiceName':
    case 'spanName': {
      let opts;
      let isLoading;
      switch (conditionKey) {
        case 'serviceName':
          opts = services;
          isLoading = isLoadingServices;
          break;
        case 'remoteServiceName':
          opts = remoteServices;
          isLoading = isLoadingRemoteServices;
          break;
        case 'spanName':
          opts = spans;
          isLoading = isLoadingSpans;
          break;
        default: break;
      }
      return (<NameCondition {...commonProps} options={opts} isLoading={isLoading} />);
    }
    case 'minDuration':
    case 'maxDuration':
      return (<DurationCondition {...commonProps} />);
    case 'tags':
      return (<TagCondition {...commonProps} />);
    default: // autocompleteTags
      return (
        <NameCondition
          {...commonProps}
          options={autocompleteValues}
          isLoading={isLoadingAutocompleteValues}
        />
      );
  }
};

GlobalSearchConditionValue.propTypes = propTypes;

export default GlobalSearchConditionValue;
