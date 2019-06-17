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
import React from 'react';
import { connect } from 'react-redux';

import NameCondition from './conditions/NameCondition';
import { globalSearchConditionsPropTypes } from '../../prop-types';
import * as globalSearchActionCreators from '../../actions/global-search-action';
import * as spansActionCreators from '../../actions/spans-action';
import * as remoteServicesActionCreators from '../../actions/remote-services-action';

const propTypes = {
  conditionIndex: PropTypes.number.isRequired,
  services: PropTypes.arrayOf(PropTypes.string).isRequired,
  remoteServices: PropTypes.arrayOf(PropTypes.string).isRequired,
  spans: PropTypes.arrayOf(PropTypes.string).isRequired,
  autocompleteValues: PropTypes.arrayOf(PropTypes.string).isRequired,
  conditions: globalSearchConditionsPropTypes.isRequired,
  onChange: PropTypes.func.isRequired,
  isFocused: PropTypes.bool.isRequired,
  onFocus: PropTypes.func.isRequired,
  onBlur: PropTypes.func.isRequired,
  fetchSpans: PropTypes.func.isRequired,
  fetchRemoteServices: PropTypes.func.isRequired,
};

const GlobalSearchConditionValue = ({
  conditionIndex,
  services,
  remoteServices,
  spans,
  autocompleteValues,
  conditions,
  onChange,
  isFocused,
  onFocus,
  onBlur,
  fetchSpans,
  fetchRemoteServices,
}) => {
  const { key: conditionKey, value: conditionValue } = conditions[conditionIndex];

  const handleValueChange = (value) => {
    onChange(conditionIndex, value);
    if (conditionKey === 'serviceName') {
      fetchRemoteServices(value);
      fetchSpans(value);
    }
  };

  const commonProps = {
    value: conditionValue,
    onChange: handleValueChange,
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
      return (
        <NameCondition
          {...commonProps}
          options={opts}
          isFocused={isFocused}
          onFocus={onFocus}
          onBlur={onBlur}
        />
      );
    }
    default: // autocompleteTags
      return (
        <NameCondition
          {...commonProps}
          options={autocompleteValues}
          isFocused={isFocused}
          onFocus={onFocus}
          onBlur={onBlur}
        />
      );
  }
};

GlobalSearchConditionValue.propTypes = propTypes;

const mapStateToProps = state => ({
  services: state.services.services,
  remoteServices: state.remoteServices.remoteServices,
  spans: state.spans.spans,
  autocompleteValues: state.autocompleteValues.autocompleteValues,
  conditions: state.globalSearch.conditions,
});

const mapDispatchToProps = (dispatch) => {
  const { changeConditionValue } = globalSearchActionCreators;
  const { fetchSpans } = spansActionCreators;
  const { fetchRemoteServices } = remoteServicesActionCreators;

  return {
    onChange: (idx, value) => dispatch(changeConditionValue(idx, value)),
    fetchSpans: serviceName => dispatch(fetchSpans(serviceName)),
    fetchRemoteServices: serviceName => dispatch(fetchRemoteServices(serviceName)),
  };
};

export default connect(
  mapStateToProps,
  mapDispatchToProps,
)(GlobalSearchConditionValue);
