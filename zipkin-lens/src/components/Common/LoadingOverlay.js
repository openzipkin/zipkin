import PropTypes from 'prop-types';
import React from 'react';
import { CSSTransition } from 'react-transition-group';

const propTypes = {
  active: PropTypes.bool.isRequired,
};

const LoadingOverlay = ({ active }) => (
  <div>
    <CSSTransition
      in={active}
      className="loading-overlay"
      classNames="loading-overlay"
      timeout={100}
      mountOnEnter
      unmountOnExit
    >
      <div>
        {
          active ? (<i className="fa fa-spinner fa-spin loading-overlay-icon" />) : null
        }
      </div>
    </CSSTransition>
  </div>
);

LoadingOverlay.propTypes = propTypes;

export default LoadingOverlay;
