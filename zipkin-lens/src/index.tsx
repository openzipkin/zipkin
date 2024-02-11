/*
 * Copyright 2015-2024 The OpenZipkin Authors
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
import { I18nextProvider } from 'react-i18next';
import React from 'react';
import ReactDOM from 'react-dom';
import App from './components/App';
import './index.css';

import i18n from './translations/i18n';

ReactDOM.render(
  <I18nextProvider i18n={i18n}>
    <App />
  </I18nextProvider>,
  document.getElementById('root'),
);
