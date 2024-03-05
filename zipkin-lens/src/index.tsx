/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
