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
