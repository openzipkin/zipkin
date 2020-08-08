/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
import React, { useContext } from 'react';

import { UI_CONFIG } from '../../constants/api';
import fetchResource from '../../util/fetch-resource';

const ConfigContext = React.createContext();

const configResource = fetchResource(
  fetch(UI_CONFIG).then((response) => response.json()),
);

const propTypes = {
  children: PropTypes.element.isRequired,
};

export const defaultConfig = {
  environment: '',
  queryLimit: 10,
  defaultLookback: 15 * 60 * 1000, // 15 minutes
  searchEnabled: true,
  dependency: {
    lowErrorRate: 0.5, // 50% of calls in error turns line yellow
    highErrorRate: 0.75, // 75% of calls in error turns line red
    enabled: true,
  },
};

export const UiConfig = ({ children }) => {
  const response = configResource.read();
  Object.keys(defaultConfig).forEach((key) => {
    if (!response[key]) {
      response[key] = defaultConfig[key];
    }
  });

  return (
    <ConfigContext.Provider value={response}>{children}</ConfigContext.Provider>
  );
};

UiConfig.propTypes = propTypes;

export const UiConfigContext = ConfigContext;
export const UiConfigConsumer = ConfigContext.Consumer;

export const useUiConfig = () => useContext(UiConfigContext);
