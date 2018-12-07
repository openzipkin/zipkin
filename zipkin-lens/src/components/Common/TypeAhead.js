import PropTypes from 'prop-types';
import React from 'react';
import ReactSelect from 'react-select';

const propTypes = {
  value: PropTypes.string,
  options: PropTypes.arrayOf(PropTypes.string).isRequired,
  onChange: PropTypes.func.isRequired,
};

const defaultProps = {
  value: null,
};

const TypeAhead = ({
  value,
  options,
  onChange,
}) => {
  const opts = options.map(
    option => ({
      value: option,
      label: option,
    }),
  );
  return (
    <ReactSelect
      onChange={
        (selected) => { onChange(selected.value); }
      }
      className="react-select-container"
      classNamePrefix="react-select"
      options={opts}
      value={{ value, label: value }}
      isSearchable
    />
  );
};


TypeAhead.propTypes = propTypes;
TypeAhead.defaultProps = defaultProps;

export default TypeAhead;
