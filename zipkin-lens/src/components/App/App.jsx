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
import React, { Suspense } from 'react';
import { Provider } from 'react-redux';
import { BrowserRouter, Route } from 'react-router-dom';
import CircularProgress from '@material-ui/core/CircularProgress';
import { ThemeProvider } from '@material-ui/styles';
import { MuiPickersUtilsProvider } from '@material-ui/pickers';
import MomentUtils from '@date-io/moment';
import { IntlProvider } from 'react-intl';

import Layout from './Layout';
import DiscoverPage from '../DiscoverPage';
import DependenciesPage from '../DependenciesPage';
import TracePage from '../TracePage';
import { UiConfig, UiConfigConsumer } from '../UiConfig';
import configureStore from '../../store/configure-store';
import { theme } from '../../colors';
import { useDocumentTitle } from '../../hooks';
import { DEFAULT_LOCALE, getLocale } from '../../util/locale';

import { BASE_PATH } from '../../constants/api';

const translations = {
  en: require('../../translations/en.json'),
  'zh-cn': require('../../translations/zh-cn.json'),
};

const locale = getLocale();
const messages = translations[locale] || translations[DEFAULT_LOCALE];

const App = () => {
  useDocumentTitle('Zipkin');
  return (
    <Suspense fallback={<CircularProgress />}>
      <UiConfig>
        <MuiPickersUtilsProvider utils={MomentUtils}>
          <ThemeProvider theme={theme}>
            <UiConfigConsumer>
              {(config) => (
                <Provider store={configureStore(config)}>
                  <IntlProvider
                    locale={locale}
                    messages={messages}
                    defaultLocale={DEFAULT_LOCALE}
                  >
                    <BrowserRouter basename={BASE_PATH}>
                      <Layout>
                        <Route
                          exact
                          path="/"
                          component={DiscoverPage}
                        />
                        <Route
                          exact
                          path="/dependency"
                          component={DependenciesPage}
                        />
                        <Route
                          exact
                          path={['/traces/:traceId', '/traceViewer']}
                          component={TracePage}
                        />
                      </Layout>
                    </BrowserRouter>
                  </IntlProvider>
                </Provider>
              )}
            </UiConfigConsumer>
          </ThemeProvider>
        </MuiPickersUtilsProvider>
      </UiConfig>
    </Suspense>
  );
};

export default App;
