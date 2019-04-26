/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import React from 'react';
import { Provider } from 'react-redux';
import { BrowserRouter, Route } from 'react-router-dom';

import Layout from './Layout';
import BrowserContainer from '../../containers/Browser/BrowserContainer';
import TracePageContainer from '../../containers/TracePage/TracePageContainer';
import DependenciesContainer from '../../containers/Dependencies/DependenciesContainer';
import TraceViewerContainer from '../../containers/TraceViewer/TraceViewerContainer';
import configureStore from '../../store/configure-store';

const applicationTitle = 'Zipkin';

class App extends React.Component {
  componentDidMount() {
    document.title = applicationTitle;
  }

  render() {
    return (
      <Provider store={configureStore()}>
        <BrowserRouter>
          <Layout>
            <Route
              exact
              path="/zipkin/"
              component={BrowserContainer}
            />
            <Route
              exact
              path="/zipkin/traces/:traceId"
              component={TracePageContainer}
            />
            <Route
              exact
              path="/zipkin/dependency"
              component={DependenciesContainer}
            />
            <Route
              exact
              path="/zipkin/traceViewer"
              render={TraceViewerContainer}
            />
          </Layout>
        </BrowserRouter>
      </Provider>
    );
  }
}

export default App;
