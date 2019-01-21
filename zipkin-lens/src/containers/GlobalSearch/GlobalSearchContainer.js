import { connect } from 'react-redux';
import { withRouter } from 'react-router';

import GlobalSearch from '../../components/GlobalSearch';
import { fetchSpans } from '../../actions/spans-action';
import { fetchServices } from '../../actions/services-action';
import { fetchTraces } from '../../actions/traces-action';
import { fetchAutocompleteKeys } from '../../actions/autocomplete-keys-action';
import { fetchAutocompleteValues } from '../../actions/autocomplete-values-action';
import {
  setLookbackCondition,
  setLimitCondition,
  addCondition,
  deleteCondition,
  changeConditionKey,
  changeConditionValue,
} from '../../actions/global-search-action';

const mapStateToProps = state => ({
  services: state.services.services,
  isLoadingServices: state.services.isLoading,
  spans: state.spans.spans,
  isLoadingSpans: state.spans.isLoading,
  conditions: state.globalSearch.conditions,
  lookbackCondition: state.globalSearch.lookbackCondition,
  limitCondition: state.globalSearch.limitCondition,
  autocompleteKeys: state.autocompleteKeys.autocompleteKeys,
  isLoadingAutocompleteKeys: state.autocompleteKeys.isLoading,
  autocompleteValues: state.autocompleteValues.autocompleteValues,
  isLoadingAutocompleteValues: state.autocompleteValues.isLoading,
});

const mapDispatchToProps = dispatch => ({
  fetchServices: () => dispatch(fetchServices()),
  fetchSpans: serviceName => dispatch(fetchSpans(serviceName)),
  fetchTraces: params => dispatch(fetchTraces(params)),
  fetchAutocompleteKeys: () => dispatch(fetchAutocompleteKeys()),
  fetchAutocompleteValues: autocompleteKey => dispatch(fetchAutocompleteValues(autocompleteKey)),
  setLookbackCondition: lookbackCondition => dispatch(setLookbackCondition(lookbackCondition)),
  setLimitCondition: limitCondition => dispatch(setLimitCondition(limitCondition)),
  addCondition: condition => dispatch(addCondition(condition)),
  deleteCondition: index => dispatch(deleteCondition(index)),
  changeConditionKey: (index, conditionKey) => {
    dispatch(changeConditionKey(index, conditionKey));
  },
  changeConditionValue: (index, conditionValue) => {
    dispatch(changeConditionValue(index, conditionValue));
  },
});

const GlobalSearchContainer = connect(
  mapStateToProps,
  mapDispatchToProps,
)(GlobalSearch);

export default withRouter(GlobalSearchContainer);
