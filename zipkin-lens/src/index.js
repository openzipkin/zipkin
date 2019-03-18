import React from 'react';
import ReactDOM from 'react-dom';
import Modal from 'react-modal';

import App from './components/App';

import '../scss/main.scss';

require('babel-polyfill');

Modal.setAppElement('body');

ReactDOM.render(
  <App />,
  document.getElementById('app'),
);
