import PropTypes from 'prop-types';
import React from 'react';

import TraceInfo from './TraceInfo';
import Timeline from '../Timeline';
import LoadingOverlay from '../Common/LoadingOverlay';
import MiniTraceViewer from './MiniTraceViewer';

const propTypes = {
  isLoading: PropTypes.bool.isRequired,
  traceId: PropTypes.string.isRequired, /* From url parameter */
  traceSummary: PropTypes.shape({}),

  fetchTrace: PropTypes.func.isRequired,
};

const defaultProps = {
  traceSummary: null,
};

class Trace extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      startTs: null,
      endTs: null,
    };
    this.handleStartAndEndTsChange = this.handleStartAndEndTsChange.bind(this);
  }

  componentDidMount() {
    this.ensureTraceFetched();
  }

  ensureTraceFetched() {
    const {
      fetchTrace,
      traceId,
      traceSummary,
    } = this.props;
    if (!traceSummary || traceSummary.traceId !== traceId) {
      fetchTrace(traceId);
    }
  }

  handleStartAndEndTsChange(startTs, endTs) {
    this.setState({
      startTs,
      endTs,
    });
  }

  render() {
    const {
      startTs,
      endTs,
    } = this.state;

    const {
      isLoading,
      traceId,
      traceSummary,
    } = this.props;

    return (
      <div>
        <LoadingOverlay active={isLoading} />
        <div className="trace">
          {
            (!traceSummary || traceSummary.traceId !== traceId)
              ? null
              : (
                <div>
                  <div className="trace__trace-info-wrapper">
                    <TraceInfo
                      traceSummary={traceSummary}
                    />
                  </div>
                  <div className="trace__mini-trace-viewer-wrapper">
                    <MiniTraceViewer
                      startTs={startTs || 0}
                      endTs={endTs || traceSummary.duration}
                      traceSummary={traceSummary}
                      onStartAndEndTsChange={this.handleStartAndEndTsChange}
                    />
                  </div>
                  <Timeline
                    startTs={startTs || 0}
                    endTs={endTs || traceSummary.duration}
                    traceSummary={traceSummary}
                  />
                </div>
              )
          }
        </div>
      </div>
    );
  }
}

Trace.propTypes = propTypes;
Trace.defaultProps = defaultProps;

export default Trace;
