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
import { I18nProvider } from '@lingui/react';
import React, { Suspense } from 'react';
import { Provider } from 'react-redux';
import { BrowserRouter, Route } from 'react-router-dom';
import CircularProgress from '@material-ui/core/CircularProgress';
import { ThemeProvider } from '@material-ui/styles';
import { MuiPickersUtilsProvider } from '@material-ui/pickers';
import MomentUtils from '@date-io/moment';

import Layout from './Layout';
import DiscoverPage from '../DiscoverPage';
import DependenciesPage from '../../dependencies/DependenciesPage';
import TracePage from '../TracePage';
import { UiConfig, UiConfigConsumer } from '../UiConfig';
import configureStore from '../../store/configure-store';
import { theme } from '../../colors';
import { useDocumentTitle } from '../../hooks';
import { i18n } from '../../util/locale';

import { BASE_PATH } from '../../constants/api';

import AlertSnackbar from './AlertSnackbar';

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
                  <AlertSnackbar />
                  <I18nProvider i18n={i18n}>
                    <BrowserRouter basename={BASE_PATH}>
                      <Layout>
                        <Route exact path="/" component={DiscoverPage} />
                        {config.dependency.enabled && (
                          <Route
                            exact
                            path="/dependency"
                            component={DependenciesPage}
                          />
                        )}
                        <Route
                          exact
                          path={['/traces/:traceId', '/traceViewer']}
                          component={TracePage}
                        />
                      </Layout>
                    </BrowserRouter>
                  </I18nProvider>
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
