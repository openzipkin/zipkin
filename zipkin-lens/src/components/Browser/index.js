import PropTypes from 'prop-types';
import React from 'react';
import ReactSelect from 'react-select';

import { sortingMethods, sortingMethodOptions, sortTraceSummaries } from './sorting';
import TraceSummary from './TraceSummary';
import LoadingOverlay from '../Common/LoadingOverlay';
import { traceSummariesPropTypes } from '../../prop-types';

const propTypes = {
  traceSummaries: traceSummariesPropTypes.isRequired,
  skewCorrectedTracesMap: PropTypes.shape({}).isRequired,
  isLoading: PropTypes.bool.isRequired,
  clearTraces: PropTypes.func.isRequired,
};

class Browser extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      sortingMethod: sortingMethods.LONGEST,
    };
    this.handleSortingMethodChange = this.handleSortingMethodChange.bind(this);
  }

  componentWillUnmount() {
    const { clearTraces } = this.props;
    clearTraces();
  }

  handleSortingMethodChange(selected) {
    this.setState({
      sortingMethod: selected.value,
    });
  }

  renderResultsHeader() {
    const { traceSummaries } = this.props;
    const { sortingMethod } = this.state;
    return (
      <div className="browser__results-header">
        <div className="browser__total-results">
          {`${traceSummaries.length} results`}
        </div>
        <div>
          <ReactSelect
            onChange={this.handleSortingMethodChange}
            className="browser__sorting-method-select"
            options={sortingMethodOptions}
            value={{
              value: sortingMethod,
              label: sortingMethodOptions.find(opt => opt.value === sortingMethod).label,
            }}
          />
        </div>
      </div>
    );
  }

  renderResults() {
    const { skewCorrectedTracesMap, traceSummaries } = this.props;
    const { sortingMethod } = this.state;
    const sortedTraceSummaries = sortTraceSummaries(traceSummaries, sortingMethod);
    return (
      <div className="browser__results">
        {
          sortedTraceSummaries.map(
            traceSummary => (
              <div
                key={traceSummary.traceId}
                className="browser__trace-summary-wrapper"
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
  }

  render() {
    const { isLoading } = this.props;
    return (
      <div className="browser">
        <LoadingOverlay active={isLoading} />
        {this.renderResultsHeader()}
        {this.renderResults()}
      </div>
    );
  }
}

Browser.propTypes = propTypes;

export default Browser;
