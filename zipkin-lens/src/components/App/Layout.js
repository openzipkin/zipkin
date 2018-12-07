import PropTypes from 'prop-types';
import React from 'react';

import Header from './Header';

const propTypes = {
  location: PropTypes.shape({}).isRequired,
  children: PropTypes.element.isRequired,
};

const Layout = ({ location, children }) => (
  <div className="app__layout">
    <Header location={location} />
    <div className="app__content">
      {children}
    </div>
  </div>
);

Layout.propTypes = propTypes;

export default Layout;
