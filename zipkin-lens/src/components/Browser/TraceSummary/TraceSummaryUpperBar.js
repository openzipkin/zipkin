import React from 'react';
import moment from 'moment';

import TraceSummaryBar from './TraceSummaryBar';
import { traceSummaryPropTypes } from '../../../prop-types';

const propTypes = {
  traceSummary: traceSummaryPropTypes.isRequired,
};

const TraceSummaryUpperBar = ({ traceSummary }) => (
  <TraceSummaryBar
    width={traceSummary.width}
    infoClass={traceSummary.infoClass}
  >
    <div className="trace-summary-upper-bar__label">
      <div className="trace-summary-upper-bar__data">
        <div className="trace-summary-upper-bar__duration" data-test="duration">
          {`${traceSummary.durationStr}`}
        </div>
        &nbsp;-&nbsp;
        <div className="trace-summary-upper-bar__spans" data-test="spans">
          {`${traceSummary.spanCount} spans`}
        </div>
      </div>
      <div data-test="timestamp">
        {moment(traceSummary.timestamp / 1000).format('MM/DD HH:mm:ss:SSS')}
      </div>
    </div>
  </TraceSummaryBar>
);

TraceSummaryUpperBar.propTypes = propTypes;

export default TraceSummaryUpperBar;
