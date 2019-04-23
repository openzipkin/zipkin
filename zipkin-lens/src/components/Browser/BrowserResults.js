import PropTypes from 'prop-types';
import React from 'react';

import TraceSummary from './TraceSummary';
import { sortTraceSummaries } from './sorting';
import { traceSummariesPropTypes } from '../../prop-types';

const propTypes = {
  traceSummaries: traceSummariesPropTypes.isRequired,
  tracesMap: PropTypes.shape({}).isRequired,
  sortingMethod: PropTypes.string.isRequired,
};

const BrowserResults = ({ traceSummaries, sortingMethod, tracesMap }) => (
  <div className="browser-results">
    {
      sortTraceSummaries(traceSummaries, sortingMethod).map(
        traceSummary => (
          <div
            key={traceSummary.traceId}
            className="browser-results__trace-summary-wrapper"
            data-test="trace-summary-wrapper"
          >
            <TraceSummary
              traceSummary={traceSummary}
              skewCorrectedTrace={tracesMap[traceSummary.traceId]}
            />
          </div>
        ),
      )
    }
  </div>
);

BrowserResults.propTypes = propTypes;

export default BrowserResults;
