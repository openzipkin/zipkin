import PropTypes from 'prop-types';
import React from 'react';
import ReactSelect from 'react-select';

import InitialMessage from './InitialMessage';
import Trace from './Trace';
import Badge from '../../Common/Badge';
import LoadingOverlay from '../../Common/LoadingOverlay';

const propTypes = {
  isLoading: PropTypes.bool.isRequired,
  clockSkewCorrectedTracesMap: PropTypes.shape({}).isRequired,
  traceSummaries: PropTypes.arrayOf(PropTypes.object).isRequired,
  location: PropTypes.shape({}).isRequired,
};

const sortOptions = [
  { value: 'LONGEST', label: 'Longest First' },
  { value: 'SHORTEST', label: 'Shortest First' },
  { value: 'NEWEST', label: 'Newest First' },
  { value: 'OLDEST', label: 'Oldest First' },
];

const convertValueToLabel = (value) => {
  const elem = sortOptions.find(
    e => e.value === value,
  );
  if (!elem) {
    return '';
  }
  return elem.label;
};

class Traces extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      filters: [],
      sort: 'LONGEST',
    };
    this.handleBadgeClick = this.handleBadgeClick.bind(this);
    this.handleSortChange = this.handleSortChange.bind(this);
  }

  getShownTraceSummaries(traceSummaries) {
    const { filters, sort } = this.state;
    return traceSummaries
      .filter((summary) => {
        for (let i = 0; i < filters.length; i += 1) {
          if (!summary.serviceSummaries.find(
            serviceSummary => serviceSummary.serviceName === filters[i],
          )) {
            return false;
          }
        }
        return true;
      })
      .sort((a, b) => {
        switch (sort) {
          case 'LONGEST':
            return b.duration - a.duration;
          case 'SHORTEST':
            return a.duration - b.duration;
          case 'NEWEST':
            return b.timestamp - a.timestamp;
          case 'OLDEST':
            return a.timestamp - b.timestamp;
          default:
            return 0;
        }
      });
  }

  isInitialState() {
    const {
      location,
    } = this.props;
    if (location.search) {
      return false;
    }
    return true;
  }

  handleBadgeClick(value) {
    const { filters } = this.state;

    if (filters.includes(value)) {
      /* Remove value */
      this.setState({ filters: filters.filter(f => f !== value) });
    } else {
      /* Add value */
      this.setState({ filters: filters.concat([value]) });
    }
  }

  handleSortChange(selected) {
    this.setState({ sort: selected.value });
  }

  renderFilters() {
    const { filters } = this.state;
    return (
      <div>
        {
          filters.map(
            filter => (
              <Badge
                key={filter}
                value={filter}
                text={filter}
                onClick={this.handleBadgeClick}
              />
            ),
          )
        }
      </div>
    );
  }

  render() {
    const {
      isLoading,
      traceSummaries,
      clockSkewCorrectedTracesMap,
    } = this.props;
    const shownTraceSummaries = this.getShownTraceSummaries(traceSummaries);
    const { sort } = this.state;
    return (
      <div>
        <LoadingOverlay active={isLoading} />
        {
          this.isInitialState()
            ? (<InitialMessage />)
            : (
              <div className="traces">
                <div className="traces__upper-box-wrapper">
                  <div className="traces__upper-box">
                    <div className="traces__total-traces-and-sorter">
                      <div>
                        {`${shownTraceSummaries.length} traces are found.`}
                      </div>
                      <div className="traces__sorter-wrapper">
                        <ReactSelect
                          onChange={this.handleSortChange}
                          className="react-select-container"
                          classNamePrefix="react-select"
                          options={sortOptions}
                          value={{ sort, label: convertValueToLabel(sort) }}
                        />
                      </div>
                    </div>
                    <div>
                      { this.renderFilters() }
                    </div>
                  </div>
                </div>
                <div>
                  {
                    shownTraceSummaries.map(
                      traceSummary => (
                        <Trace
                          key={traceSummary.traceId}
                          traceSummary={traceSummary}
                          clockSkewCorrectedTrace={
                            clockSkewCorrectedTracesMap[traceSummary.traceId]
                          }
                          handleBadgeClick={this.handleBadgeClick}
                        />
                      ),
                    )
                  }
                </div>
              </div>
            )
        }
      </div>
    );
  }
}

Traces.propTypes = propTypes;

export default Traces;
