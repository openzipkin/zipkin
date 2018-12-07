import PropTypes from 'prop-types';
import React from 'react';

const propTypes = {
  disabled: PropTypes.bool,
  children: PropTypes.oneOfType([
    PropTypes.element,
    PropTypes.string,
  ]).isRequired,
  className: PropTypes.string,
  onClick: PropTypes.func,
  style: PropTypes.shape({}),
};

const defaultProps = {
  disabled: false,
  className: '',
  onClick: null,
  style: null,
};

const Button = ({
  disabled,
  children,
  className,
  onClick,
  style,
}) => (
  <button
    type="button"
    style={style}
    className={`btn ${className} ${disabled ? 'disabled' : ''}`}
    onClick={(e) => {
      if (onClick) {
        onClick(e);
      }
    }}
    disabled={disabled}
  >
    {children}
  </button>
);

Button.propTypes = propTypes;
Button.defaultProps = defaultProps;

export default Button;
