/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import PropTypes from 'prop-types';
import React, { useContext } from 'react';

import { UI_CONFIG } from '../../constants/api';
import fetchResource from '../../util/fetch-resource';

import { defaultConfig } from './constants';

const ConfigContext = React.createContext();

const configResource = fetchResource(
  fetch(UI_CONFIG).then((response) => response.json()),
);

const propTypes = {
  children: PropTypes.element.isRequired,
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
