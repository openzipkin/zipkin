import PropTypes from 'prop-types';
import React from 'react';

const propTypes = {
  label: PropTypes.string.isRequired,
  children: PropTypes.element.isRequired,
};

const Condition = ({
  label,
  children,
}) => (
  <div className="search__condition">
    <div className="search__condition-label">
      {label}
    </div>
    {children}
  </div>
);

Condition.propTypes = propTypes;

export default Condition;
