import { connect } from 'react-redux';
import { withRouter } from 'react-router';

import GlobalSearch from '../../components/GlobalSearch';
import { fetchSpans } from '../../actions/spans-action';
import { fetchServices } from '../../actions/services-action';
import { fetchTraces } from '../../actions/traces-action';
import {
  setLookbackCondition,
  setLimitCondition,
  addCondition,
  deleteCondition,
  changeConditionKey,
  changeConditionValue,
} from '../../actions/global-search-action';

const mapStateToProps = state => ({
  spans: state.spans.spans,
  services: state.services.services,
  conditions: state.globalSearch.conditions,
  lookbackCondition: state.globalSearch.lookbackCondition,
  limitCondition: state.globalSearch.limitCondition,
});

const mapDispatchToProps = dispatch => ({
  fetchServices: () => dispatch(fetchServices()),
  fetchSpans: serviceName => dispatch(fetchSpans(serviceName)),
  fetchTraces: params => dispatch(fetchTraces(params)),
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
