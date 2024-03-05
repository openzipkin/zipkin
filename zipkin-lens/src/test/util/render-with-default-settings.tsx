/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import MomentUtils from '@date-io/moment';
import { ThemeProvider as MuiThemeProvider } from '@material-ui/styles';
import MuiPickersUtilsProvider from '@material-ui/pickers/MuiPickersUtilsProvider';
// @ts-ignore
import { createMemoryHistory, History } from 'history';
import React from 'react';
import { Provider } from 'react-redux';
import { Router } from 'react-router-dom';
import { ThemeProvider } from 'styled-components';

import { render } from '@testing-library/react';
import { UiConfigContext } from '../../components/UiConfig';
import { theme } from '../../constants/color';
import configureStore from '../../store/configure-store';

interface RenderProps {
  route?: string;
  history?: History;
  uiConfig?: object;
}

export default (
  ui: React.ReactElement,
  {
    route = '/',
    history = createMemoryHistory({ initialEntries: [route] }),
    uiConfig = {},
  }: RenderProps = {},
) => {
  const store = configureStore({});

  const filledConfig = {
    // Defaults copied from ZipkinUiCOnfiguration.java
    environment: '',
    queryLimit: 10,
    defaultLookback: 15 * 60 * 1000,
    searchEnabled: true,
    dependency: {
      enabled: true,
      lowErrorRate: 0.5,
      highErrorRate: 0.75,
    },
    ...uiConfig,
  };

  const wrapper: React.FunctionComponent = ({ children }) => (
    <Provider store={store}>
      <Router history={history as any}>
        <MuiPickersUtilsProvider utils={MomentUtils}>
          <ThemeProvider theme={theme}>
            <MuiThemeProvider theme={theme}>
              <UiConfigContext.Provider value={filledConfig}>
                {children}
              </UiConfigContext.Provider>
            </MuiThemeProvider>
          </ThemeProvider>
        </MuiPickersUtilsProvider>
      </Router>
    </Provider>
  );
  return {
    ...render(ui, { wrapper }),
    history,
    store,
  };
};
