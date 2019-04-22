import PropTypes from 'prop-types';
import React from 'react';

import TraceSummary from './TraceSummary';
import { sortTraceSummaries } from './sorting';
import { traceSummariesPropTypes } from '../../prop-types';

const propTypes = {
  traceSummaries: traceSummariesPropTypes.isRequired,
  skewCorrectedTracesMap: PropTypes.shape({}).isRequired,
  sortingMethod: PropTypes.string.isRequired,
};

const BrowserResults = ({ traceSummaries, sortingMethod, skewCorrectedTracesMap }) => (
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
              skewCorrectedTrace={skewCorrectedTracesMap[traceSummary.traceId]}
            />
          </div>
        ),
      )
    }
  </div>
);

BrowserResults.propTypes = propTypes;

export default BrowserResults;
