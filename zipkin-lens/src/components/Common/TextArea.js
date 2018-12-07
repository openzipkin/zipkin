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

const propTypes = {
  rows: PropTypes.number,
  value: PropTypes.string,
  className: PropTypes.string,
  onChange: PropTypes.func,
  placeholder: PropTypes.string,
};

const defaultProps = {
  rows: 1,
  value: '',
  className: '',
  onChange: null,
  placeholder: '',
};

const TextArea = ({
  rows,
  value,
  className,
  onChange,
  placeholder,
}) => (
  <textarea
    type="text"
    rows={rows.toString()}
    value={value}
    className={`form-textarea ${className}`}
    placeholder={placeholder}
    onChange={
      (event) => {
        onChange(event.target.value);
      }
    }
  />
);

TextArea.propTypes = propTypes;
TextArea.defaultProps = defaultProps;

export default TextArea;
