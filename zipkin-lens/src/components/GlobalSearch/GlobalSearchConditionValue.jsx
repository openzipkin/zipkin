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

  const services = useSelector(state => state.services.services);
  const remoteServices = useSelector(state => state.remoteServices.remoteServices);
  const spans = useSelector(state => state.spans.spans);
  const autocompleteValues = useSelector(state => state.autocompleteValues.autocompleteValues);
  const conditions = useSelector(state => state.globalSearch.conditions);

  const { key: conditionKey, value: conditionValue } = conditions[conditionIndex];

  const handleValueChange = (value) => {
    dispatch(changeConditionValue(conditionIndex, value));
    if (conditionKey === 'serviceName') {
      dispatch(fetchRemoteServices(value));
      dispatch(fetchSpans(value));
    }
  };

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
      switch (conditionKey) {
        case 'serviceName': opts = services; break;
        case 'remoteServiceName': opts = remoteServices; break;
        case 'spanName': opts = spans; break;
        default: break;
      }
      return (<NameCondition {...commonProps} options={opts} />);
    }
    case 'minDuration':
    case 'maxDuration':
      return (<DurationCondition {...commonProps} />);
    case 'tags':
      return (<TagCondition {...commonProps} />);
    default: // autocompleteTags
      return (<NameCondition {...commonProps} options={autocompleteValues} />);
  }
};

GlobalSearchConditionValue.propTypes = propTypes;

export default GlobalSearchConditionValue;
