/*
 * Copyright 2018 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

import PropTypes from 'prop-types';
import React from 'react';
import ReactSelect from 'react-select';

const propTypes = {
  value: PropTypes.string,
  options: PropTypes.arrayOf(PropTypes.string).isRequired,
  onChange: PropTypes.func.isRequired,
};

const defaultProps = {
  value: null,
};

const TypeAhead = ({
  value,
  options,
  onChange,
}) => {
  const opts = options.map(
    option => ({
      value: option,
      label: option,
    }),
  );
  return (
    <ReactSelect
      onChange={
        (selected) => { onChange(selected.value); }
      }
      className="react-select-container"
      classNamePrefix="react-select"
      options={opts}
      value={{ value, label: value }}
      isSearchable
    />
  );
};


TypeAhead.propTypes = propTypes;
TypeAhead.defaultProps = defaultProps;

export default TypeAhead;
