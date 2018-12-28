import React from 'react';
import { Provider } from 'react-redux';
import { BrowserRouter, Route } from 'react-router-dom';

import Layout from './Layout';
import BrowserContainer from '../../containers/Browser/BrowserContainer';
import DetailedTraceSummaryContainer from '../../containers/DetailedTraceSummary/DetailedTraceSummaryContainer';
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
              <DetailedTraceSummaryContainer {...props} />
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
