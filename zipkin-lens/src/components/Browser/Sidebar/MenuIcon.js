import PropTypes from 'prop-types';
import React from 'react';
import { CSSTransition } from 'react-transition-group';

const propTypes = {
  isActive: PropTypes.bool.isRequired,
  onToggle: PropTypes.func.isRequired,
};

const MenuIcon = ({ isActive, onToggle }) => (
  <div
    className="sidebar__menu-icon"
    onClick={onToggle}
    role="presentation"
  >
    <CSSTransition
      in={isActive}
      classNames="sidebar__menu-icon-upper-bar"
      timeout={500}
    >
      <span className="sidebar__menu-icon-upper-bar" />
    </CSSTransition>
    <CSSTransition
      in={isActive}
      classNames="sidebar__menu-icon-middle-bar"
      timeout={500}
    >
      <span className="sidebar__menu-icon-middle-bar" />
    </CSSTransition>
    <CSSTransition
      in={isActive}
      classNames="sidebar__menu-icon-lower-bar"
      timeout={500}
    >
      <span className="sidebar__menu-icon-lower-bar" />
    </CSSTransition>
  </div>
);

MenuIcon.propTypes = propTypes;

export default MenuIcon;
