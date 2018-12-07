import PropTypes from 'prop-types';
import React from 'react';

const propTypes = {
  type: PropTypes.string,
  value: PropTypes.string,
  className: PropTypes.string,
  placeholder: PropTypes.string,
  onChange: PropTypes.func,
  onKeyPress: PropTypes.func,
};

const defaultProps = {
  type: 'text',
  value: '',
  className: '',
  placeholder: '',
  onChange: null,
  onKeyPress: null,
};

const Input = ({
  type,
  value,
  className,
  placeholder,
  onChange,
  onKeyPress,
}) => (
  <input
    type={type}
    value={value}
    className={`form-input ${className}`}
    placeholder={placeholder}
    onChange={
      (event) => {
        if (onChange) {
          onChange(event.target.value);
        }
      }
    }
    onKeyPress={
      (event) => {
        if (onKeyPress) {
          onKeyPress(event);
        }
      }
    }
  />
);

Input.propTypes = propTypes;
Input.defaultProps = defaultProps;

export default Input;
