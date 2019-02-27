import Cookies from 'js-cookie';
import PropTypes from 'prop-types';
import React from 'react';
import { Link } from 'react-router-dom';

import Logo from '../../img/zipkin-logo.svg';

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
    url: '/zipkin/dependency',
    label: 'Dependencies',
    icon: 'fa-code-branch',
  },
};

class Sidebar extends React.Component {
  // Before Zipkin 2.13, Lens was optionally available based on a cookie named 'lens'.
  // To revert to the classic UI, remove the cookie and reload.
  goBackToClassic(evt) {
    evt.preventDefault();
    Cookies.remove('lens');
    this.window.location.reload(true);
  }

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
        <div className="sidebar__other-links">
          <a href="https://zipkin.apache.org/" target="_blank" rel="noopener noreferrer">
            <div className="sidebar__other-link fas fa-home" />
          </a>
          <a href="https://github.com/openzipkin/zipkin" target="_blank" rel="noopener noreferrer">
            <div className="sidebar__other-link fab fa-github" />
          </a>
          <a href="https://twitter.com/zipkinproject" target="_blank" rel="noopener noreferrer">
            <div className="sidebar__other-link fab fa-twitter" />
          </a>
          <a href="https://gitter.im/openzipkin/zipkin/" target="_blank" rel="noopener noreferrer">
            <div className="sidebar__other-link fab fa-gitter" />
          </a>
        </div>
        {Cookies.get('lens')
        && (
          <div>
            <button type="button" onClick={this.goBackToClassic.bind(this)}>
              <span>Go back to classic Zipkin</span>
            </button>
          </div>
        )}
      </div>
    );
  }
}

Sidebar.propTypes = propTypes;

export default Sidebar;
