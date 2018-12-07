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
  disabled: PropTypes.bool,
  children: PropTypes.oneOfType([
    PropTypes.element,
    PropTypes.string,
  ]).isRequired,
  className: PropTypes.string,
  onClick: PropTypes.func,
  style: PropTypes.shape({}),
};

const defaultProps = {
  disabled: false,
  className: '',
  onClick: null,
  style: null,
};

const Button = ({
  disabled,
  children,
  className,
  onClick,
  style,
}) => (
  <button
    type="button"
    style={style}
    className={`btn ${className} ${disabled ? 'disabled' : ''}`}
    onClick={(e) => {
      if (onClick) {
        onClick(e);
      }
    }}
    disabled={disabled}
  >
    {children}
  </button>
);

Button.propTypes = propTypes;
Button.defaultProps = defaultProps;

export default Button;
