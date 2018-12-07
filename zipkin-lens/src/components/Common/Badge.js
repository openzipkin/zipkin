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

import { getServiceNameColor } from '../../util/color';

const propTypes = {
  value: PropTypes.string.isRequired,
  text: PropTypes.string.isRequired,
  className: PropTypes.string,
  onClick: PropTypes.func,
};

const defaultProps = {
  className: '',
  onClick: null,
};

const Badge = ({
  value,
  text,
  onClick,
  className,
}) => {
  const color = getServiceNameColor(value);
  const style = { background: color };
  return (
    <span
      className={`badge ${className}`}
      onClick={
        (e) => {
          if (onClick) {
            e.stopPropagation();
            onClick(value);
          }
        }
      }
      role="presentation"
    >
      <span style={style} className="badge__color" />
      {text}
    </span>
  );
};

Badge.propTypes = propTypes;
Badge.defaultProps = defaultProps;

export default Badge;
