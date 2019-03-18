import PropTypes from 'prop-types';
import React from 'react';

import LoadingOverlay from '../Common/LoadingOverlay';
import DetailedTraceSummary from '../DetailedTraceSummary';
import { detailedTraceSummaryPropTypes } from '../../prop-types';

const propTypes = {
  isLoading: PropTypes.bool.isRequired,
  traceId: PropTypes.string.isRequired,
  traceSummary: detailedTraceSummaryPropTypes,
  fetchTrace: PropTypes.func.isRequired,
};

const defaultProps = {
  traceSummary: null,
};

class TracePage extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      startTs: null,
      endTs: null,
    };
    this.handleStartAndEndTsChange = this.handleStartAndEndTsChange.bind(this);
  }

  componentDidMount() {
    const { fetchTrace, traceId, traceSummary } = this.props;
    if (!traceSummary || traceSummary.traceId !== traceId) {
      fetchTrace(traceId);
    }
  }

  handleStartAndEndTsChange(startTs, endTs) {
    this.setState({ startTs, endTs });
  }

  render() {
    const { startTs, endTs } = this.state;
    const { isLoading, traceId, traceSummary } = this.props;
    return (
      <div className="trace-page">
        <LoadingOverlay active={isLoading} />
        <div className="trace-page__detailed-trace-summary-wrapper">
          {
            (!traceSummary || traceSummary.traceId !== traceId)
              ? null
              : (
                <DetailedTraceSummary
                  startTs={startTs}
                  endTs={endTs}
                  onStartAndEndTsChange={this.handleStartAndEndTsChange}
                  traceSummary={traceSummary}
                />
              )
          }
        </div>
      </div>
    );
  }
}

TracePage.propTypes = propTypes;
TracePage.defaultProps = defaultProps;

export default TracePage;
