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
import MomentUtils from '@date-io/moment';
import {
  CircularProgress,
  ThemeProvider as MuiThemeProvider,
} from '@material-ui/core';
import { MuiPickersUtilsProvider } from '@material-ui/pickers';
import React, { Suspense, useMemo } from 'react';
import { Provider } from 'react-redux';
import { BrowserRouter, Route } from 'react-router-dom';
import { useTitle } from 'react-use';
import { ThemeProvider } from 'styled-components';

import Layout from './Layout';
import DependenciesPage from '../DependenciesPage';
import DiscoverPage from '../DiscoverPage';
import TracePage from '../TracePage';
import { UiConfig, UiConfigConsumer } from '../UiConfig';
import configureStore from '../../store/configure-store';
import { theme } from '../../constants/color';
import AlertSnackbar from './AlertSnackbar';

const App: React.FC = () => {
  useTitle('Zipkin');
  const baseName = useMemo(() => {
    return import.meta.env.DEV
      ? '/zipkin'
      : (import.meta.env.BASE_PATH as string);
  }, []);

  return (
    <Suspense fallback={<CircularProgress />}>
      <UiConfig>
        <MuiPickersUtilsProvider utils={MomentUtils}>
          <ThemeProvider theme={theme}>
            <MuiThemeProvider theme={theme}>
              <UiConfigConsumer>
                {(config) => (
                  <Provider store={configureStore(config)}>
                    <AlertSnackbar />
                    <BrowserRouter basename={baseName}>
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
                  </Provider>
                )}
              </UiConfigConsumer>
            </MuiThemeProvider>
          </ThemeProvider>
        </MuiPickersUtilsProvider>
      </UiConfig>
    </Suspense>
  );
};

export default App;
