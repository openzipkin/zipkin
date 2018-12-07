import PropTypes from 'prop-types';
import React from 'react';

const propTypes = {
  rows: PropTypes.number,
  value: PropTypes.string,
  className: PropTypes.string,
  onChange: PropTypes.func,
  placeholder: PropTypes.string,
};

const defaultProps = {
  rows: 1,
  value: '',
  className: '',
  onChange: null,
  placeholder: '',
};

const TextArea = ({
  rows,
  value,
  className,
  onChange,
  placeholder,
}) => (
  <textarea
    type="text"
    rows={rows.toString()}
    value={value}
    className={`form-textarea ${className}`}
    placeholder={placeholder}
    onChange={
      (event) => {
        onChange(event.target.value);
      }
    }
  />
);

TextArea.propTypes = propTypes;
TextArea.defaultProps = defaultProps;

export default TextArea;
