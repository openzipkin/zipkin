/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
import { BASE_PATH } from '../../constants/api';

const App: React.FC = () => {
  useTitle('Zipkin');
  const baseName = useMemo(() => {
    return import.meta.env.DEV ? '/zipkin' : BASE_PATH;
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
