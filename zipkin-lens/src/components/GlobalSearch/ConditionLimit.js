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
import NumericInput from 'react-numeric-input';

const propTypes = {
  limit: PropTypes.number.isRequired,
  onLimitChange: PropTypes.func.isRequired,
};

const formatter = num => `Max ${num}`;
const parser = stringValue => stringValue.replace(/Max /, '');

const style = {
  btn: {
    borderStyle: 'none',
    background: 'rgba(0,0,0,0)',
    boxShadow: 'none',
  },

  wrap: {
    width: '100%',
    height: '100%',
  },

  input: {
    width: '100%',
    height: '100%',
    background: 'rgba(0,0,0,0)',
  },

  'input:not(.form-control)': {
    border: 'none',
    borderRadius: 0,
  },
};

const ConditionLimit = ({ limit, onLimitChange }) => (
  <NumericInput
    min={0}
    max={250}
    value={limit}
    onChange={onLimitChange}
    format={formatter}
    parse={parser}
    style={style}
    strict
  />
);

ConditionLimit.propTypes = propTypes;

export default ConditionLimit;
