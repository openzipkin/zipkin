import React from 'react';
import ReactDOM from 'react-dom';

import App from './components/App';

import '../scss/main.scss';

require('babel-polyfill');

ReactDOM.render(
  <App />,
  document.getElementById('app'),
);
