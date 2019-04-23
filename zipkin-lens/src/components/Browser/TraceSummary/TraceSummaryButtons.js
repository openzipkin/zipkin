import PropTypes from 'prop-types';
import React from 'react';
import { Link } from 'react-router-dom';

import * as api from '../../../constants/api';

const propTypes = {
  traceId: PropTypes.string.isRequired,
};

const TraceSummaryButtons = ({ traceId }) => (
  <div className="trace-summary-buttons">
    <a href={`${api.TRACE}/${traceId}`} target="_brank" data-test="download">
      <i className="fas fa-file-download" />
    </a>
    <Link to={{ pathname: `/zipkin/traces/${traceId}` }} data-test="trace">
      <i className="fas fa-angle-double-right" />
    </Link>
  </div>
);

TraceSummaryButtons.propTypes = propTypes;

export default TraceSummaryButtons;
