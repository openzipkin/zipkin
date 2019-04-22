import PropTypes from 'prop-types';
import React from 'react';

import { sortingMethods } from './sorting';
import BrowserHeader from './BrowserHeader';
import BrowserResults from './BrowserResults';
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
    this.state = { sortingMethod: sortingMethods.LONGEST };
    this.handleSortingMethodChange = this.handleSortingMethodChange.bind(this);
  }

  componentWillUnmount() {
    const { clearTraces } = this.props;
    clearTraces();
  }

  handleSortingMethodChange(selected) {
    this.setState({ sortingMethod: selected.value });
  }

  render() {
    const { isLoading, traceSummaries, skewCorrectedTracesMap } = this.props;
    const { sortingMethod } = this.state;
    return (
      <div className="browser">
        <LoadingOverlay active={isLoading} />
        <BrowserHeader
          numTraces={traceSummaries.length}
          sortingMethod={sortingMethod}
          onChange={this.handleSortingMethodChange}
        />
        <BrowserResults
          traceSummaries={traceSummaries}
          sortingMethod={sortingMethod}
          skewCorrectedTracesMap={skewCorrectedTracesMap}
        />
      </div>
    );
  }
}

Browser.propTypes = propTypes;

export default Browser;
