import PropTypes from 'prop-types';
import React from 'react';
import RCInputNumber from 'rc-input-number';
import { CSSTransition } from 'react-transition-group';
import { Link } from 'react-router-dom';
import queryString from 'query-string';

import Condition from './Condition';
import TypeAhead from '../../../Common/TypeAhead';
import Lookback from './Lookback';
import Button from '../../../Common/Button';
import Input from '../../../Common/Input';
import TextArea from '../../../Common/TextArea';
import { buildQueryParameters } from '../../../../util/api';

const propTypes = {
  spans: PropTypes.arrayOf(PropTypes.string).isRequired,
  services: PropTypes.arrayOf(PropTypes.string).isRequired,
  isActive: PropTypes.bool.isRequired,
  location: PropTypes.shape({}).isRequired,

  fetchServices: PropTypes.func.isRequired,
  fetchSpans: PropTypes.func.isRequired,
};

class Search extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      serviceName: null,
      spanName: null,
      limit: 10,
      annotationQuery: '',
      endTs: null,
      lookback: null,
      minDuration: '',
    };

    this.handleServiceNameChange = this.handleServiceNameChange.bind(this);
    this.handleSpanNameChange = this.handleSpanNameChange.bind(this);
    this.handleLimitChange = this.handleLimitChange.bind(this);
    this.handleAnnotationQueryChange = this.handleAnnotationQueryChange.bind(this);
    this.handleEndTsChange = this.handleEndTsChange.bind(this);
    this.handleLookbackChange = this.handleLookbackChange.bind(this);
    this.handleMinDurationChange = this.handleMinDurationChange.bind(this);
  }

  componentDidMount() {
    const {
      fetchServices, location, fetchSpans,
    } = this.props;
    fetchServices();

    const queryParams = queryString.parse(location.search.substr(1));
    this.setState({
      serviceName: queryParams.serviceName,
      spanName: queryParams.spanName,
      limit: queryParams.limit,
      annotationQuery: queryParams.annotationQuery,
      minDuration: queryParams.minDuration,
    });

    if (queryParams.serviceName) {
      fetchSpans(queryParams.serviceName);
    }
  }

  handleServiceNameChange(serviceName) {
    this.setState({
      serviceName,
      spanName: null,
    });

    const { fetchSpans } = this.props;
    fetchSpans(serviceName);
  }

  handleSpanNameChange(spanName) {
    this.setState({ spanName });
  }

  handleLimitChange(limit) {
    this.setState({ limit });
  }

  handleAnnotationQueryChange(annotationQuery) {
    this.setState({ annotationQuery });
  }

  handleEndTsChange(endTs) {
    this.setState({ endTs });
  }

  handleLookbackChange(lookback) {
    this.setState({ lookback });
  }

  handleMinDurationChange(minDuration) {
    this.setState({ minDuration });
  }

  renderFindButton() {
    const {
      serviceName,
      spanName,
      limit,
      annotationQuery,
      endTs,
      lookback,
      minDuration,
    } = this.state;

    const query = buildQueryParameters({
      serviceName: serviceName !== 'all' ? serviceName : null,
      spanName: spanName !== 'all' ? spanName : null,
      limit,
      annotationQuery,
      endTs,
      lookback,
      minDuration,
    });

    return (
      <div className="search__find-button-wrapper">
        <Link to={{ pathname: '', search: query }}>
          <Button className="search__find-button">
            Find Traces
          </Button>
        </Link>
      </div>
    );
  }

  render() {
    const {
      isActive, spans, services,
    } = this.props;

    const {
      serviceName,
      spanName,
      limit,
      annotationQuery,
      minDuration,
    } = this.state;

    const spanOptions = spans.slice();
    spanOptions.unshift('all');

    const serviceOptions = services.slice();
    serviceOptions.unshift('all');

    return (
      <div className="search">
        <CSSTransition
          in={isActive}
          classNames="search__contents"
          timeout={150}
        >
          <div>
            <Condition label="Service Name">
              <TypeAhead
                value={serviceName}
                onChange={this.handleServiceNameChange}
                options={serviceOptions}
              />
            </Condition>
            <Condition label="Span Name">
              <TypeAhead
                value={spanName}
                onChange={this.handleSpanNameChange}
                options={spanOptions}
              />
            </Condition>
            <Condition label="Lookback">
              <Lookback
                onEndTsChange={this.handleEndTsChange}
                onLookbackChange={this.handleLookbackChange}
              />
            </Condition>
            <Condition label="Limit">
              <RCInputNumber
                value={limit}
                min={10}
                max={200}
                onChange={this.handleLimitChange}
              />
            </Condition>
            <Condition label="Annotation Query">
              <TextArea
                rows={5}
                className="search__annotation-query"
                placeholder="e.g. cache.miss and phase=beta"
                value={annotationQuery}
                onChange={this.handleAnnotationQueryChange}
              />
            </Condition>
            <Condition label="Min Duration (μs) >=">
              <Input
                value={minDuration}
                onChange={this.handleMinDurationChange}
              />
            </Condition>
            { this.renderFindButton() }
          </div>
        </CSSTransition>
      </div>
    );
  }
}

Search.propTypes = propTypes;

export default Search;
