import PropTypes from 'prop-types';
import React from 'react';

const propTypes = {
  traceSummary: PropTypes.shape({}).isRequired,
};

const TraceInfo = ({ traceSummary }) => (
  <div className="trace__trace-info">
    {
      [
        { label: 'Duration', value: traceSummary.durationStr },
        { label: 'Services', value: traceSummary.services },
        { label: 'Depth', value: traceSummary.depth },
        { label: 'Total Spans', value: traceSummary.totalSpans },
      ].map(elem => (
        <div
          key={elem.label}
          className="trace__trace-info-info"
        >
          <div className="trace__trace-info-label">
            {elem.label}
          </div>
          <div className="trace__trace-info-value">
            {elem.value}
          </div>
        </div>
      ))
    }
  </div>
);


TraceInfo.propTypes = propTypes;

export default TraceInfo;
