/*
 * Copyright 2018 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

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
