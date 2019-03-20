import React from 'react';
import { Provider } from 'react-redux';
import { BrowserRouter, Route } from 'react-router-dom';

import Layout from './Layout';
import BrowserContainer from '../../containers/Browser/BrowserContainer';
import DetailedTraceSummaryContainer from '../../containers/DetailedTraceSummary/DetailedTraceSummaryContainer';
import DependenciesContainer from '../../containers/Dependencies/DependenciesContainer';
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
              render={props => (
                <BrowserContainer {...props} />
              )}
            />
            <Route
              exact
              path="/zipkin/traces/:traceId"
              render={props => (
                <DetailedTraceSummaryContainer {...props} />
              )}
            />
            <Route
              exact
              path="/zipkin/dependency"
              render={props => (
                <DependenciesContainer {...props} />
              )}
            />
          </Layout>
        </BrowserRouter>
      </Provider>
    );
  }
}

export default App;
