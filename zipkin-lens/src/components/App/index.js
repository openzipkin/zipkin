/*
 * Copyright 2018 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

import React from 'react';
import { Provider } from 'react-redux';
import { BrowserRouter, Route } from 'react-router-dom';

import Layout from './Layout';
import BrowserContainer from '../../containers/Browser/BrowserContainer';
import TraceContainer from '../../containers/Trace/TraceContainer';
import DependenciesContainer from '../../containers/Dependencies/DependenciesContainer';
import configureStore from '../../store/configure-store';

const App = () => (
  <Provider store={configureStore()}>
    <BrowserRouter>
      <div>
        <Route
          exact
          path="/zipkin"
          render={props => (
            <Layout {...props}>
              <BrowserContainer {...props} />
            </Layout>
          )}
        />
        <Route
          exact
          path="/zipkin/trace/:traceId"
          render={props => (
            <Layout {...props}>
              <TraceContainer {...props} />
            </Layout>
          )}
        />
        <Route
          exact
          path="/zipkin/dependencies"
          render={props => (
            <Layout {...props}>
              <DependenciesContainer {...props} />
            </Layout>
          )}
        />
      </div>
    </BrowserRouter>
  </Provider>
);

export default App;
