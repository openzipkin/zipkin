import PropTypes from 'prop-types';
import React from 'react';
import { Link } from 'react-router-dom';

import TraceId from './TraceId';

const propTypes = {
  location: PropTypes.shape({}).isRequired,
};

const Header = ({ location }) => {
  const isBrowserSelected = location.pathname === '/zipkin';
  const isDependenciesSelected = location.pathname === '/zipkin/dependencies';

  return (
    <header className="header">
      <div className="header__contents">
        <div className="header__brand">
          <Link to={{ pathname: '/zipkin' }}>
            <div className="header__brand-title">
              Zipkin Lens
            </div>
          </Link>
        </div>
        <div className="header__menu">
          <div className={`header__option ${isBrowserSelected ? 'selected' : ''}`}>
            <Link
              to={{ pathname: '/zipkin' }}
              className={`header__option-link ${isBrowserSelected ? 'selected' : ''}`}
            >
              <i className="fas fa-search header__option-icon" />
              Search
            </Link>
          </div>
          <div className={`header__option ${isDependenciesSelected ? 'selected' : ''}`}>
            <Link
              to={{ pathname: '/zipkin/dependencies' }}
              className={`header__option-link ${isDependenciesSelected ? 'selected' : ''}`}
            >
              <i className="fas fa-code-branch header__option-icon" />
              Dependencies
            </Link>
          </div>
        </div>
        <TraceId />
      </div>
    </header>
  );
};

Header.propTypes = propTypes;

export default Header;
