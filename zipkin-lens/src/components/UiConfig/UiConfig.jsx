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
import React, { useContext } from 'react';

import { UI_CONFIG } from '../../constants/api';
import fetchResource from '../../util/fetch-resource';

const ConfigContext = React.createContext();

const configResource = fetchResource(fetch(UI_CONFIG).then(response => response.json()));

export const UiConfig = ({ children }) => {
  const response = configResource.read();
  return (
    <ConfigContext.Provider value={response}>
      {children}
    </ConfigContext.Provider>
  );
};

export const UiConfigContext = ConfigContext;
export const UiConfigConsumer = ConfigContext.Consumer;

export const useUiConfig = () => useContext(UiConfigContext);
