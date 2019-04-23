import PropTypes from 'prop-types';
import React from 'react';
import ReactSelect from 'react-select';

import { sortingMethodOptions } from './sorting';

const propTypes = {
  numTraces: PropTypes.number.isRequired,
  sortingMethod: PropTypes.string.isRequired,
  onChange: PropTypes.func.isRequired,
};

const BrowserHeader = ({ numTraces, sortingMethod, onChange }) => (
  <div className="browser-header">
    <div
      className="browser-header__total-results"
      data-test="total-results"
    >
      {`${numTraces} results`}
    </div>
    <ReactSelect
      onChange={onChange}
      className="browser-header__sorting-methods"
      options={sortingMethodOptions}
      value={{
        value: sortingMethod,
        label: sortingMethodOptions.find(opt => opt.value === sortingMethod).label,
      }}
    />
  </div>
);

BrowserHeader.propTypes = propTypes;

export default BrowserHeader;
