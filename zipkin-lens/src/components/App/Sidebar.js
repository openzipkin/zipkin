import PropTypes from 'prop-types';
import React from 'react';
import { Link } from 'react-router-dom';

import Logo from 'assets/logo';

const propTypes = {
  location: PropTypes.shape({
    pathname: PropTypes.string,
  }).isRequired,
};

const pageInfo = {
  browser: {
    url: '/zipkin',
    label: 'Search',
    icon: 'fa-search',
  },
  dependencies: {
    url: '/zipkin/dependencies',
    label: 'Dependencies',
    icon: 'fa-code-branch',
  },
};

class Sidebar extends React.Component {
  renderPageOption(pageName) {
    const { location } = this.props;
    const { url } = pageInfo[pageName];
    const isSelected = location.pathname === url;
    return (
      <div className={`sidebar__page-option${isSelected ? '--selected' : ''}`}>
        <Link
          to={{ pathname: url }}
          className={`sidebar__page-option-link${isSelected ? '--selected' : ''}`}
        >
          <div className="sidebar__page-option-icon">
            <i className={`fas ${pageInfo[pageName].icon}`} />
          </div>
          <div>
            {pageInfo[pageName].label}
          </div>
        </Link>
      </div>
    );
  }

  render() {
    return (
      <div className="sidebar">
        <div
          to={{ pathname: '' }}
          className="sidebar__brand-link"
        >
          <Logo className="sidebar__brand-logo" />
        </div>
        <div className="sidebar__menu">
          {this.renderPageOption('browser')}
          {this.renderPageOption('dependencies')}
        </div>
      </div>
    );
  }
}

Sidebar.propTypes = propTypes;

export default Sidebar;
