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
  orderedConditionKeyList,
  buildQueryParametersWithConditions,
  buildApiQueryParameters,
  extractConditionsFromQueryParameters,
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
  fetchSpans: PropTypes.func.isRequired,
  fetchTraces: PropTypes.func.isRequired,
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
  }

  componentDidMount() {
    const {
      fetchServices,
      fetchSpans,
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
    this.fetchTraces(location);
  }

  componentWillReceiveProps({ location }) {
    const { location: previousLocation } = this.props;
    if (location.search !== '' && location.search !== '?' && previousLocation.search !== location.search) {
      this.fetchTraces(location);
    }
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

  // Make the availability with the already specified condition being false,
  // the condition not specified yet being true.
  getConditionListWithAvailability(currentConditionKey) {
    const { conditions } = this.props;
    const existingConditionsMemo = {};

    // Memo the keys which is already used.
    conditions.forEach((condition) => {
      if (condition.key === 'annotationQuery') {
        return;
      }
      existingConditionsMemo[condition.key] = true;
    });

    const result = [];
    for (let i = 0; i < orderedConditionKeyList.length; i += 1) {
      const conditionKey = orderedConditionKeyList[i];

      // The currently focused conditionName is also available.
      if (conditionKey === currentConditionKey) {
        result.push({ conditionKey, isAvailable: true });
        continue;
      }

      let isAvailable = false;
      if (!existingConditionsMemo[conditionKey]) {
        isAvailable = true;
      }
      result.push({
        conditionKey,
        isAvailable,
      });
    }
    return result;
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
    const { addCondition } = this.props;
    addCondition();
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
    const { changeConditionKey } = this.props;
    changeConditionKey(index, conditionKey);
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

  renderCondition(conditionName, index, value) {
    const { services, spans } = this.props;
    const commonProps = {
      value,
      onConditionChange: (val) => { this.handleConditionValueChange(index, val); },
    };

    switch (conditionName) {
      case 'serviceName':
      case 'spanName': {
        const options = conditionName === 'serviceName' ? services : spans;
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
      default:
        // Do nothing
        return null;
    }
  }

  render() {
    const { conditions, lookbackCondition, limitCondition } = this.props;
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
                  <SearchCondition
                    keyString={condition.key}
                    keyOptions={this.getConditionListWithAvailability(condition.key)}
                    onConditionKeyChange={
                      (keyName) => { this.handleConditionKeyChange(index, keyName); }
                    }
                    onDeleteButtonClick={() => { this.handleDeleteConditionButtonClick(index); }}
                  >
                    {
                      this.renderCondition(condition.key, index, condition.value)
                    }
                  </SearchCondition>
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
