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

import { getServiceNameColor } from '../../util/color';

const propTypes = {
  serviceName: PropTypes.string.isRequired,
  count: PropTypes.number,
  className: PropTypes.string,
};

const defaultProps = {
  count: null,
  className: '',
};

const ServiceNameBadge = ({
  serviceName,
  count,
  className,
}) => {
  const style = {
    backgroundColor: getServiceNameColor(serviceName),
  };
  const text = count ? `${serviceName} x ${count}` : serviceName;
  return (
    <span
      style={style}
      className={`service-name-badge ${className}`}
    >
      {text}
    </span>
  );
};

ServiceNameBadge.propTypes = propTypes;
ServiceNameBadge.defaultProps = defaultProps;

export default ServiceNameBadge;
