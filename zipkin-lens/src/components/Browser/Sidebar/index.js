import PropTypes from 'prop-types';
import React from 'react';

import MenuIcon from './MenuIcon';
import SearchContainer from '../../../containers/Browser/Sidebar/SearchContainer';

const propTypes = {
  isShown: PropTypes.bool.isRequired,
  onToggle: PropTypes.func.isRequired,
  location: PropTypes.shape({}).isRequired,
};

const Sidebar = ({ isShown, onToggle, location }) => (
  <nav className="sidebar">
    <div className="sidebar__contents">
      <MenuIcon
        isActive={isShown}
        onToggle={onToggle}
      />
      <SearchContainer
        isActive={isShown}
        location={location}
      />
    </div>
  </nav>
);

Sidebar.propTypes = propTypes;

export default Sidebar;
