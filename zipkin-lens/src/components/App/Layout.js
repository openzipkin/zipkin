import PropTypes from 'prop-types';
import React from 'react';
import { withRouter } from 'react-router';

import Sidebar from './Sidebar';
import GlobalSearchContainer from '../../containers/GlobalSearch/GlobalSearchContainer';

const propTypes = {
  location: PropTypes.shape({}).isRequired,
  children: PropTypes.arrayOf(PropTypes.element).isRequired,
};

const Layout = ({ location, children }) => (
  <div className="app__layout">
    <Sidebar location={location} />
    <div className="app__global-search-wrapper">
      <GlobalSearchContainer />
    </div>
    <div className="app__content">
      {children}
    </div>
  </div>
);

Layout.propTypes = propTypes;

export default withRouter(Layout);
