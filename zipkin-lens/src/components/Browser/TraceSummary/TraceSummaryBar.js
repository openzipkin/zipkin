import PropTypes from 'prop-types';
import React from 'react';

import { getInfoClassColor } from '../../../util/color';

const propTypes = {
  children: PropTypes.node.isRequired,
  width: PropTypes.number.isRequired,
  infoClass: PropTypes.string.isRequired,
};

const TraceSummaryBar = ({ children, width, infoClass }) => (
  <div className="trace-summary-bar">
    <div
      className="trace-summary-bar__bar-wrapper"
      style={{ width: `${width}%` }}
      data-test="bar-wrapper"
    >
      <div
        className="trace-summary-bar__bar"
        style={{ backgroundColor: getInfoClassColor(infoClass) }}
        data-test="bar"
      />
    </div>
    <div className="trace-summary-bar__label-wrapper">
      {children}
    </div>
  </div>
);

TraceSummaryBar.propTypes = propTypes;

export default TraceSummaryBar;
