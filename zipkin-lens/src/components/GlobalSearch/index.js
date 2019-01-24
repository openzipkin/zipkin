import PropTypes from 'prop-types';
import React from 'react';
import { withRouter } from 'react-router';
import moment from 'moment';
import queryString from 'query-string';

import SearchCondition from './SearchCondition';
import ConditionDuration from './ConditionDuration';
import ConditionLimit from './ConditionLimit';
import ConditionName from './ConditionName';
import ConditionAnnotationQuery from './ConditionAnnotationQuery';
import ConditionLookback from './ConditionLookback';
import {
  isAutocompleteKey,
  defaultConditionValues,
  getConditionKeyListWithAvailability,
  buildQueryParametersWithConditions,
  buildApiQueryParameters,
  extractConditionsFromQueryParameters,
  nextInitialConditionKey,
} from '../../util/global-search';

const propTypes = {
  services: PropTypes.arrayOf(PropTypes.string).isRequired,
  spans: PropTypes.arrayOf(PropTypes.string).isRequired,
  conditions: PropTypes.arrayOf(PropTypes.shape({
    key: PropTypes.string,
    value: PropTypes.oneOfType([
      PropTypes.string,
      PropTypes.number,
      PropTypes.shape({}),
    ]),
  })).isRequired,
  lookbackCondition: PropTypes.shape({
    value: PropTypes.string,
    endTs: PropTypes.number,
    startTs: PropTypes.number,
  }).isRequired,
  limitCondition: PropTypes.number.isRequired,
  fetchServices: PropTypes.func.isRequired,
  isLoadingServices: PropTypes.bool.isRequired,
  fetchSpans: PropTypes.func.isRequired,
  isLoadingSpans: PropTypes.bool.isRequired,
  fetchTraces: PropTypes.func.isRequired,
  fetchAutocompleteKeys: PropTypes.func.isRequired,
  fetchAutocompleteValues: PropTypes.func.isRequired,
  autocompleteKeys: PropTypes.arrayOf(PropTypes.string).isRequired,
  isLoadingAutocompleteKeys: PropTypes.bool.isRequired,
  autocompleteValues: PropTypes.arrayOf(PropTypes.string).isRequired,
  isLoadingAutocompleteValues: PropTypes.bool.isRequired,
  setLookbackCondition: PropTypes.func.isRequired,
  setLimitCondition: PropTypes.func.isRequired,
  addCondition: PropTypes.func.isRequired,
  deleteCondition: PropTypes.func.isRequired,
  changeConditionKey: PropTypes.func.isRequired,
  changeConditionValue: PropTypes.func.isRequired,
  location: PropTypes.shape({
    search: PropTypes.string.isRequired,
  }).isRequired,
  history: PropTypes.shape({
    push: PropTypes.func.isRequired,
  }).isRequired,
};

class GlobalSearch extends React.Component {
  constructor(props) {
    super(props);

    this.handleAddButtonClick = this.handleAddButtonClick.bind(this);
    this.handleSearchButtonClick = this.handleSearchButtonClick.bind(this);
    this.handleDeleteConditionButtonClick = this.handleDeleteConditionButtonClick.bind(this);
    this.handleConditionKeyChange = this.handleConditionKeyChange.bind(this);
    this.handleConditionValueChange = this.handleConditionValueChange.bind(this);
    this.handleLookbackChange = this.handleLookbackChange.bind(this);
    this.handleLimitChange = this.handleLimitChange.bind(this);
    this.handleKeyPress = this.handleKeyPress.bind(this);
  }

  componentDidMount() {
    document.addEventListener('keydown', this.handleKeyPress);
    const {
      fetchServices,
      fetchSpans,
      fetchAutocompleteKeys,
      location,
      addCondition,
      setLookbackCondition,
      setLimitCondition,
    } = this.props;

    const initialConditions = this.getConditionsFromQueryParameters();
    const { conditions, lookbackCondition, limitCondition } = initialConditions;
    conditions.forEach((condition) => {
      addCondition(condition);
    });
    setLookbackCondition({
      value: lookbackCondition.value || '1h',
      endTs: lookbackCondition.endTs || moment().valueOf(),
      startTs: lookbackCondition.startTs || moment().valueOf(),
    });
    setLimitCondition(limitCondition || 10);

    fetchServices();
    const serviceNameCondition = initialConditions.conditions.find(condition => condition.key === 'serviceName');
    if (serviceNameCondition) {
      fetchSpans(serviceNameCondition.value);
    }
    fetchAutocompleteKeys();
    this.fetchTraces(location);
  }

  componentWillReceiveProps({ location }) {
    const { location: previousLocation } = this.props;
    if (location.search !== '' && location.search !== '?' && previousLocation.search !== location.search) {
      this.fetchTraces(location);
    }
  }

  componentWillUnmount() {
    document.removeEventListener('keydown', this.handleKeyPress);
  }

  getConditionsFromQueryParameters() {
    const { location } = this.props;
    if (location.search !== '' && location.search !== '?') {
      const queryParameters = queryString.parse(location.search);
      return extractConditionsFromQueryParameters(queryParameters);
    }
    return {
      conditions: [],
      lookbackCondition: {},
      limitCondition: null,
    };
  }

  fetchTraces(location) {
    const { fetchTraces } = this.props;
    if (location.search !== '' && location.search !== '?') {
      const queryParameters = queryString.parse(location.search);
      const apiQueryParameters = buildApiQueryParameters(queryParameters);
      fetchTraces(apiQueryParameters);
    }
  }

  handleAddButtonClick() {
    const { addCondition, conditions, autocompleteKeys } = this.props;
    const nextConditionKey = nextInitialConditionKey(conditions, autocompleteKeys);
    addCondition({
      key: nextConditionKey,
      value: defaultConditionValues(nextConditionKey),
    });
  }

  handleKeyPress(event) {
    if (event.key === 'Enter') {
      this.handleSearchButtonClick();
    }
  }

  handleSearchButtonClick() {
    const {
      history, conditions, lookbackCondition, limitCondition,
    } = this.props;
    const queryParams = buildQueryParametersWithConditions(
      conditions,
      lookbackCondition,
      limitCondition,
    );
    history.push({
      pathname: '/zipkin',
      search: queryParams,
    });
  }

  // Replaces the key of the "index"-th condition with "keyName" and clear value.
  handleConditionKeyChange(index, conditionKey) {
    const {
      changeConditionKey,
      fetchAutocompleteValues,
    } = this.props;
    changeConditionKey(index, conditionKey);

    if (isAutocompleteKey(conditionKey)) {
      fetchAutocompleteValues(conditionKey);
    }
  }

  // Replaces the value of the "index"-th condition with "value".
  handleConditionValueChange(index, conditionValue) {
    const {
      fetchSpans,
      conditions,
      changeConditionValue,
    } = this.props;

    changeConditionValue(index, conditionValue);
    if (conditions[index].key === 'serviceName') {
      fetchSpans(conditionValue);
    }
  }

  handleDeleteConditionButtonClick(index) {
    const { deleteCondition } = this.props;
    deleteCondition(index);
  }

  handleLookbackChange(lookbackCondition) {
    const { setLookbackCondition } = this.props;
    setLookbackCondition(lookbackCondition);
  }

  handleLimitChange(limitCondition) {
    const { setLimitCondition } = this.props;
    setLimitCondition(limitCondition);
  }

  renderCondition(conditionKey, index, value) {
    const {
      services,
      spans,
      isLoadingServices,
      isLoadingSpans,
      autocompleteValues,
      isLoadingAutocompleteValues,
    } = this.props;
    const commonProps = {
      value,
      onConditionChange: (val) => { this.handleConditionValueChange(index, val); },
    };

    switch (conditionKey) {
      case 'serviceName':
      case 'spanName': {
        let options;
        let isLoadingOptions;
        if (conditionKey === 'serviceName') {
          options = services;
          isLoadingOptions = isLoadingServices;
        } else {
          options = spans;
          isLoadingOptions = isLoadingSpans;
        }
        return ({
          onFocus, onBlur, setNextFocusRef, isFocused,
        }) => (
          <ConditionName
            {...commonProps}
            options={options}
            onFocus={onFocus}
            onBlur={onBlur}
            setNextFocusRef={setNextFocusRef}
            isFocused={isFocused}
            isLoadingOptions={isLoadingOptions}
          />
        );
      }
      case 'minDuration':
      case 'maxDuration':
        return ({
          onFocus, onBlur, setNextFocusRef, isFocused,
        }) => (
          <ConditionDuration
            {...commonProps}
            onFocus={onFocus}
            onBlur={onBlur}
            setNextFocusRef={setNextFocusRef}
            isFocused={isFocused}
          />
        );
      case 'annotationQuery':
        return ({
          onFocus, onBlur, setNextFocusRef, isFocused,
        }) => (
          <ConditionAnnotationQuery
            {...commonProps}
            onFocus={onFocus}
            onBlur={onBlur}
            setNextFocusRef={setNextFocusRef}
            isFocused={isFocused}
          />
        );
      default: // autocompleteTags
        return ({
          onFocus, onBlur, setNextFocusRef, isFocused,
        }) => (
          <ConditionName
            {...commonProps}
            options={autocompleteValues}
            onFocus={onFocus}
            onBlur={onBlur}
            setNextFocusRef={setNextFocusRef}
            isFocused={isFocused}
            isLoadingOptions={isLoadingAutocompleteValues}
          />
        );
    }
  }

  renderSearchCondition(condition, index) {
    const {
      conditions,
      autocompleteKeys,
      fetchAutocompleteValues,
    } = this.props;

    const commonProps = {
      keyString: condition.key,
      keyOptions: getConditionKeyListWithAvailability(condition.key, conditions, autocompleteKeys),
      onConditionKeyChange: (conditionKey) => {
        this.handleConditionKeyChange(index, conditionKey);
      },
      onDeleteButtonClick: () => { this.handleDeleteConditionButtonClick(index); },
    };

    if (isAutocompleteKey(condition.key)) {
      return (
        <SearchCondition
          {...commonProps}
          onKeyFocus={() => { fetchAutocompleteValues(condition.key); }}
          onValueFocus={() => { fetchAutocompleteValues(condition.key); }}
        >
          { this.renderCondition(condition.key, index, condition.value) }
        </SearchCondition>
      );
    }
    return (
      <SearchCondition {...commonProps}>
        { this.renderCondition(condition.key, index, condition.value) }
      </SearchCondition>
    );
  }

  render() {
    const {
      conditions,
      lookbackCondition,
      limitCondition,
    } = this.props;
    return (
      <div className="global-search">
        <div className="global-search__conditions">
          {
            conditions.length === 0
              ? (
                <div className="global-search__placeholder">
                  Please select the criteria for your trace lookup.
                </div>
              )
              : conditions.map((condition, index) => (
                <div
                  key={condition._id}
                  className="global-search__search-condition-wrapper"
                >
                  { this.renderSearchCondition(condition, index) }
                </div>
              ))
          }
          <div
            role="presentation"
            onClick={this.handleAddButtonClick}
            className="global-search__add-condition-button"
          >
            <span className="fas fa-plus global-search__add-condition-button-icon" />
          </div>
          <div
            role="presentation"
            onClick={this.handleSearchButtonClick}
            className="global-search__find-button"
          >
            <span className="fas fa-search global-search__find-button-icon" />
          </div>
        </div>
        <div className="global-search__condition-limit-wrapper">
          <ConditionLimit
            limit={limitCondition}
            onLimitChange={this.handleLimitChange}
          />
        </div>
        <div className="global-search__condition-lookback-wrapper">
          <ConditionLookback
            lookback={lookbackCondition}
            onLookbackChange={this.handleLookbackChange}
          />
        </div>
      </div>
    );
  }
}

GlobalSearch.propTypes = propTypes;

export default withRouter(GlobalSearch);
