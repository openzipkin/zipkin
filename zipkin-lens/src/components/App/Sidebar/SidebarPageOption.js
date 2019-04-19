import PropTypes from 'prop-types';
import React from 'react';
import { Link } from 'react-router-dom';

const propTypes = {
  location: PropTypes.shape({
    pathname: PropTypes.string.isRequired,
  }).isRequired,
  pageName: PropTypes.string.isRequired,
};

const pageData = {
  browser: {
    url: '/zipkin',
    label: 'Search',
    icon: 'fas fa-search',
  },
  dependencies: {
    url: '/zipkin/dependency',
    label: 'Dependencies',
    icon: 'fas fa-code-branch',
  },
};

const SidebarPageOption = ({ location, pageName }) => {
  const isSelected = location.pathname === pageData[pageName].url;
  return (
    <div className={`sidebar__page-option ${isSelected ? 'sidebar__page-option--selected' : ''}`}>
      <Link to={{ pathname: pageData[pageName].url }}>
        <div className="sidebar__page-option-icon">
          <i className={pageData[pageName].icon} />
        </div>
        <div className="sidebar__page-option-label">
          {pageData[pageName].label}
        </div>
      </Link>
    </div>
  );
};

SidebarPageOption.propTypes = propTypes;

export default SidebarPageOption;
