import PropTypes from 'prop-types';
import React from 'react';
import { withRouter } from 'react-router';
import moment from 'moment';
import queryString from 'query-string';
import shortid from 'shortid';

import SearchCondition from './SearchCondition';
import ConditionDuration from './ConditionDuration';
import ConditionLimit from './ConditionLimit';
import ConditionName from './ConditionName';
import ConditionAnnotationQuery from './ConditionAnnotationQuery';
import ConditionLookback from './ConditionLookback';
import { buildQueryParameters } from '../../util/api';

const conditionList = [
  'serviceName',
  'spanName',
  'minDuration',
  'maxDuration',
  'annotationQuery',
];

const lookbackDurations = {
  '1h': 3600000,
  '2h': 7200000,
  '6h': 21600000,
  '12h': 43200000,
  '1d': 86400000,
  '2d': 172800000,
  '7d': 604800000,
};

const defaultConditionValues = {
  serviceName: 'all',
  spanName: 'all',
  minDuration: 10,
  maxDuration: 100,
  annotationQuery: '',
};

const propTypes = {
  services: PropTypes.arrayOf(PropTypes.string).isRequired,
  spans: PropTypes.arrayOf(PropTypes.string).isRequired,
  fetchServices: PropTypes.func.isRequired,
  fetchSpans: PropTypes.func.isRequired,
  fetchTraces: PropTypes.func.isRequired,
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

    const { fetchSpans } = props;

    const initialConditions = this.getConditionsFromQueryParameters();
    this.state = {
      conditions: initialConditions.conditions,
      lookbackCondition: {
        value: initialConditions.lookbackCondition.value || '1h',
        endTs: initialConditions.lookbackCondition.endTs || moment().valueOf(),
        startTs: initialConditions.lookbackCondition.startTs || moment().valueOf(),
      },
      limitCondition: initialConditions.limitCondition || 10,
    };
    const serviceNameCondition = initialConditions.conditions.find(condition => condition.key === 'serviceName');
    if (serviceNameCondition) {
      fetchSpans(serviceNameCondition.value);
    }

    this.handleAddButtonClick = this.handleAddButtonClick.bind(this);
    this.handleSearchButtonClick = this.handleSearchButtonClick.bind(this);
    this.handleDeleteConditionButtonClick = this.handleDeleteConditionButtonClick.bind(this);
    this.handleConditionKeyChange = this.handleConditionKeyChange.bind(this);
    this.handleConditionValueChange = this.handleConditionValueChange.bind(this);
    this.handleLookbackChange = this.handleLookbackChange.bind(this);
    this.handleLimitChange = this.handleLimitChange.bind(this);
  }

  componentDidMount() {
    const { fetchServices, location } = this.props;
    fetchServices();
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
    const conditions = [];
    const lookbackCondition = {};
    let limitCondition = 10;

    if (location.search !== '' && location.search !== '?') {
      const queryParameters = queryString.parse(location.search);
      Object.keys(queryParameters).forEach((conditionKey) => {
        const conditionValue = queryParameters[conditionKey];
        switch (conditionKey) {
          case 'serviceName':
          case 'spanName':
          case 'minDuration':
          case 'maxDuration':
            conditions.push({
              _id: shortid.generate(),
              key: conditionKey,
              value: conditionValue,
            });
            break;
          case 'annotationQuery':
            conditionValue.split(' and ').forEach((annotationQuery) => {
              conditions.push({
                _id: shortid.generate(),
                key: conditionKey,
                value: annotationQuery,
              });
            });
            break;
          case 'limit':
            limitCondition = parseInt(conditionValue, 10);
            break;
          case 'lookback':
            switch (conditionValue) {
              case '1h':
              case '2h':
              case '6h':
              case '12h':
              case '1d':
              case '2d':
              case '7d': {
                lookbackCondition.value = conditionValue;
                lookbackCondition.endTs = parseInt(queryParameters.endTs, 10);
                break;
              }
              case 'custom':
                lookbackCondition.value = conditionValue;
                lookbackCondition.endTs = parseInt(queryParameters.endTs, 10);
                lookbackCondition.startTs = parseInt(queryParameters.startTs, 10);
                break;
              default:
                break;
            }
            break;
          default:
            break;
        }
      });
    }
    return { conditions, lookbackCondition, limitCondition };
  }

  // Returns the condition that first appears when the Add condition
  // button is clicked.
  getNextInitialConditionKey() {
    const { services, spans } = this.props;
    const { conditions } = this.state;
    const conditionMap = {};

    conditions.forEach((condition) => {
      conditionMap[condition.key] = true;
    });

    for (let i = 0; i < conditionList.length; i += 1) {
      const conditionName = conditionList[i];
      if (!conditionMap[conditionName]) {
        switch (conditionName) {
          // If the key is serviceName or spanName, skip if there are no options.
          // Probably this approach is best for UX.
          case 'serviceName':
            if (services.length === 0) {
              continue;
            }
            return conditionName;
          case 'spanName':
            if (spans.length === 0) {
              continue;
            }
            return conditionName;
          default: // else always return the condition name.
            return conditionName;
        }
      }
    }
    // Return annotationQuery if all conditions are already set
    return 'annotationQuery';
  }

  // Make the availability with the already specified condition being false,
  // the condition not specified yet being true.
  getConditionListWithAvailability(currentConditionName) {
    const { services, spans } = this.props;
    const { conditions } = this.state;
    const conditionMap = {};

    // Memo the keys which is already used.
    conditions.forEach((condition) => {
      if (condition.key === 'annotationQuery') {
        return;
      }
      conditionMap[condition.key] = true;
    });

    const result = [];
    for (let i = 0; i < conditionList.length; i += 1) {
      const conditionName = conditionList[i];

      // The currently focused conditionName is also available.
      if (conditionName === currentConditionName) {
        result.push({ name: conditionName, isAvailable: true });
        continue;
      }

      let isAvailable = false;
      if (!conditionMap[conditionName]) {
        switch (conditionName) {
          // If the key is serviceName or spanName, it is
          // unavailable when there is no options.
          case 'serviceName':
            if (services.length > 0) {
              isAvailable = true;
            } else {
              isAvailable = false;
            }
            break;
          case 'spanName':
            if (spans.length > 0) {
              isAvailable = true;
            } else {
              isAvailable = false;
            }
            break;
          default: // Else always available.
            isAvailable = true;
            break;
        }
      }
      result.push({
        name: conditionName,
        isAvailable,
      });
    }
    return result;
  }

  fetchTraces(location) {
    const { fetchTraces } = this.props;
    if (location.search !== '' && location.search !== '?') {
      const queryParameters = queryString.parse(location.search);
      const apiQueryParameters = {};

      Object.keys(queryParameters).forEach((conditionKey) => {
        const conditionValue = queryParameters[conditionKey];
        switch (conditionKey) {
          case 'serviceName':
          case 'spanName':
          case 'minDuration':
          case 'maxDuration':
          case 'annotationQuery':
          case 'limit':
            apiQueryParameters[conditionKey] = conditionValue;
            break;
          case 'lookback':
            switch (conditionValue) {
              case '1h':
              case '2h':
              case '6h':
              case '12h':
              case '1d':
              case '2d':
              case '7d':
                apiQueryParameters.endTs = queryParameters.endTs;
                apiQueryParameters.lookback = lookbackDurations[conditionValue];
                break;
              case 'custom':
                apiQueryParameters.endTs = queryParameters.endTs;
                apiQueryParameters.lookback = queryParameters.endTs - queryParameters.startTs;
                break;
              default:
                break;
            }
            break;
          default:
            break;
        }
      });
      fetchTraces(apiQueryParameters);
    }
  }

  handleAddButtonClick() {
    const nextKey = this.getNextInitialConditionKey();
    const { fetchSpans } = this.props;

    const condition = {
      key: nextKey,
      value: defaultConditionValues[nextKey],
    };

    this.setState(prevState => ({
      conditions: [...prevState.conditions, {
        _id: shortid.generate(), // For element unique key
        key: nextKey,
        value: defaultConditionValues[nextKey],
      }],
    }));

    if (nextKey === 'serviceName') {
      fetchSpans(condition.value);
    }
  }

  handleSearchButtonClick() {
    const { history } = this.props;
    const { conditions, lookbackCondition, limitCondition } = this.state;
    const annotationQueryConditions = [];
    const conditionMap = {};

    conditions.forEach((condition) => {
      if (condition.key === 'annotationQuery') {
        annotationQueryConditions.push(condition.value);
      } else {
        conditionMap[condition.key] = condition.value;
      }
    });
    conditionMap.annotationQuery = annotationQueryConditions.join(' and ');

    conditionMap.limit = limitCondition;
    conditionMap.lookback = lookbackCondition.value;
    conditionMap.endTs = lookbackCondition.endTs;
    if (lookbackCondition.value === 'custom') {
      conditionMap.startTs = lookbackCondition.startTs;
    }

    const queryParams = buildQueryParameters(conditionMap);
    history.push({
      pathname: '/zipkin',
      search: queryParams,
    });
  }

  // Replaces the key of the "index"-th condition with "keyName" and
  // Clear value.
  handleConditionKeyChange(index, keyName) {
    const { fetchSpans } = this.props;
    const { conditions: prevConditions } = this.state;
    const conditions = [...prevConditions];
    const condition = { ...conditions[index] };
    condition.key = keyName;
    condition.value = defaultConditionValues[keyName];
    conditions[index] = condition;
    this.setState({ conditions });

    if (condition.key === 'serviceName') {
      fetchSpans(condition.value);
    }
  }

  // Replaces the value of the "index"-th condition with "value".
  handleConditionValueChange(index, value) {
    const { fetchSpans } = this.props;
    const { conditions: prevConditions } = this.state;
    const conditions = [...prevConditions];
    const condition = { ...conditions[index] };
    condition.value = value;
    conditions[index] = condition;
    this.setState({ conditions });

    if (condition.key === 'serviceName') {
      fetchSpans(condition.value);
    }
  }

  handleDeleteConditionButtonClick(index) {
    const { conditions: prevConditions } = this.state;
    const conditions = [...prevConditions];
    conditions.splice(index, 1);
    this.setState({ conditions });
  }

  handleLookbackChange(lookbackCondition) {
    this.setState({ lookbackCondition });
  }

  handleLimitChange(limitCondition) {
    this.setState({ limitCondition });
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
    const { conditions, lookbackCondition, limitCondition } = this.state;
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
