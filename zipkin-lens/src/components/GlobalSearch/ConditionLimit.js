import PropTypes from 'prop-types';
import React from 'react';
import NumericInput from 'react-numeric-input';

const propTypes = {
  limit: PropTypes.number.isRequired,
  onLimitChange: PropTypes.func.isRequired,
};

const formatter = num => `Max ${num}`;
const parser = stringValue => stringValue.replace(/Max /, '');

const style = {
  btn: {
    borderStyle: 'none',
    background: 'rgba(0,0,0,0)',
    boxShadow: 'none',
  },

  wrap: {
    width: '100%',
    height: '100%',
  },

  input: {
    width: '100%',
    height: '100%',
    background: 'rgba(0,0,0,0)',
  },

  'input:not(.form-control)': {
    border: 'none',
    borderRadius: 0,
  },
};

const ConditionLimit = ({ limit, onLimitChange }) => (
  <NumericInput
    min={0}
    max={250}
    value={limit}
    onChange={onLimitChange}
    format={formatter}
    parse={parser}
    style={style}
    strict
  />
);

ConditionLimit.propTypes = propTypes;

export default ConditionLimit;
