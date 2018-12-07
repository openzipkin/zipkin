import PropTypes from 'prop-types';
import React from 'react';
import { Link } from 'react-router-dom';
import { CSSTransition } from 'react-transition-group';
import queryString from 'query-string';
import moment from 'moment';

import DependenciesGraph from './DependenciesGraph';
import Sidebar from './Sidebar';
import Button from '../Common/Button';
import DatePicker from '../Common/DatePicker';
import LoadingOverlay from '../Common/LoadingOverlay';
import TypeAhead from '../Common/TypeAhead';
import { buildQueryParameters } from '../../util/api';
import Graph from '../../util/dependencies-graph';

const propTypes = {
  location: PropTypes.shape({}).isRequired,
  isLoading: PropTypes.bool.isRequired,
  dependencies: PropTypes.arrayOf(PropTypes.shape({})).isRequired,
  fetchDependencies: PropTypes.func.isRequired,
  clearDependencies: PropTypes.func.isRequired,
};

class Dependencies extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      startTs: moment(),
      endTs: moment(),
      detailedService: undefined,
      searchString: '',
    };

    this.handleStartTsChange = this.handleStartTsChange.bind(this);
    this.handleEndTsChange = this.handleEndTsChange.bind(this);
    this.handleDetailedServiceChange = this.handleDetailedServiceChange.bind(this);
    this.handleSearchStringChange = this.handleSearchStringChange.bind(this);
  }

  componentDidMount() {
    const {
      location,
      fetchDependencies,
    } = this.props;

    const queryParams = queryString.parse(location.search.substr(1));
    const endTs = queryParams.endTs ? moment(parseInt(queryParams.endTs, 10)) : moment();
    const lookback = queryParams.lookback ? parseInt(queryParams.lookback, 10) : 0;
    const startTs = moment(endTs.valueOf() - lookback);
    this.setState({
      startTs,
      endTs,
    });

    if (location.search !== '' && location.search !== '?') {
      const query = queryString.parse(location.search);
      fetchDependencies(query);
    }
  }

  componentWillReceiveProps({ location }) {
    const {
      location: prevLocation,
      fetchDependencies,
    } = this.props;

    if (location.search !== '' && location.search !== '?' && prevLocation.search !== location.search) {
      const query = queryString.parse(location.search);
      fetchDependencies(query);
    }
  }

  componentWillUnmount() {
    const {
      clearDependencies,
    } = this.props;

    clearDependencies();
  }

  handleStartTsChange(startTs) {
    this.setState({ startTs });
  }

  handleEndTsChange(endTs) {
    this.setState({ endTs });
  }

  handleDetailedServiceChange(detailedService) {
    this.setState({ detailedService });
  }

  handleSearchStringChange(searchString) {
    this.setState({ searchString });
  }

  render() {
    const {
      isLoading,
      dependencies,
    } = this.props;

    const {
      startTs,
      endTs,
      detailedService,
      searchString,
    } = this.state;

    const graph = new Graph(dependencies);

    return (
      <div className="dependencies__wrapper">
        <div className="dependencies__background" />
        <LoadingOverlay active={isLoading} />
        <CSSTransition
          in={typeof detailedService === 'undefined'}
          className="dependencies__main"
          classNames="dependencies__main"
          timeout={500}
        >
          <div className="dependencies">
            <div className="dependencies__search-wrapper">
              <div className="dependencies__search">
                <div className="dependencies__search-condition">
                  <div className="dependencies__search-label">
                    From
                  </div>
                  <DatePicker
                    onChange={this.handleStartTsChange}
                    selected={startTs}
                  />
                </div>
                <div className="dependencies__search-condition">
                  <div className="dependencies__search-label">
                    To
                  </div>
                  <DatePicker
                    onChange={this.handleEndTsChange}
                    selected={endTs}
                  />
                </div>
                <div className="dependencies__search-button-wrapper">
                  <Link to={{
                    pathname: '/zipkin/dependencies',
                    search: buildQueryParameters({
                      endTs: endTs.valueOf(),
                      lookback: endTs.valueOf() - startTs.valueOf(),
                    }),
                  }}
                  >
                    <Button className="dependencies__search-button">
                      Analize Dependencies
                    </Button>
                  </Link>
                </div>
              </div>
            </div>
            {
              graph.allNodes().length === 0
                ? null
                : (
                  <div>
                    <div className="dependencies__type-ahead-box-wrapper">
                      <div className="dependencies__type-ahead-box">
                        <i className="fas fa-search header__menu-option-link-icon" />
                        Search
                        &nbsp;
                        <div className="dependencies__type-ahead-wrapper">
                          <TypeAhead
                            className="dependencies_type-ahead"
                            onChange={this.handleSearchStringChange}
                            value={searchString}
                            options={graph.allNodeNames()}
                          />
                        </div>
                      </div>
                    </div>
                    <div className="dependencies__graph-wrapper">
                      <DependenciesGraph
                        graph={graph}
                        onDetailedServiceChange={this.handleDetailedServiceChange}
                        detailedService={detailedService}
                        searchString={searchString}
                      />
                    </div>
                  </div>
                )
            }
          </div>
        </CSSTransition>
        <CSSTransition
          in={typeof detailedService === 'undefined'}
          className="dependencies__sidebar"
          classNames="dependencies__sidebar"
          timeout={500}
        >
          {
            typeof detailedService === 'undefined' ? <div />
              : (
                <Sidebar
                  isActive={typeof detailedService === 'undefined'}
                  detailedService={detailedService}
                  graph={graph}
                  onSearchStringChange={this.handleSearchStringChange}
                />
              )
          }
        </CSSTransition>
      </div>
    );
  }
}

Dependencies.propTypes = propTypes;

export default Dependencies;
