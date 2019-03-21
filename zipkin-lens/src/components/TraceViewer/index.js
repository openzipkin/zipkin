import PropTypes from 'prop-types';
import React from 'react';

import DetailedTraceSummary from '../DetailedTraceSummary';
import { detailedTraceSummaryPropTypes } from '../../prop-types';

const propTypes = {
  traceSummary: detailedTraceSummaryPropTypes,
  isMalformedFile: PropTypes.bool.isRequired,
  errorMessage: PropTypes.string.isRequired,
};

const defaultProps = {
  traceSummary: null,
};

class TraceViewer extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      startTs: null,
      endTs: null,
    };
    this.handleStartAndEndTsChange = this.handleStartAndEndTsChange.bind(this);
  }

  handleStartAndEndTsChange(startTs, endTs) {
    this.setState({ startTs, endTs });
  }

  render() {
    const { startTs, endTs } = this.state;
    const { traceSummary, isMalformedFile, errorMessage } = this.props;

    if (isMalformedFile) {
      return (
        <div className="trace-viewer__error-message-wrapper">
          <div className="trace-viewer__error-message">
            Error:
            &nbsp;
            {errorMessage}
          </div>
        </div>
      );
    }

    return (
      <div className="trace-viewer">
        {
          traceSummary ? (
            <DetailedTraceSummary
              startTs={startTs}
              endTs={endTs}
              onStartAndEndTsChange={this.handleStartAndEndTsChange}
              traceSummary={traceSummary}
            />
          ) : null
        }
      </div>
    );
  }
}

TraceViewer.propTypes = propTypes;
TraceViewer.defaultProps = defaultProps;

export default TraceViewer;
