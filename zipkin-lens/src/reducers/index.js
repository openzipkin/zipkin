import { combineReducers } from 'redux';

import spans from './spans';
import trace from './trace';
import traces from './traces';
import services from './services';
import dependencies from './dependencies';
import globalSearch from './global-search';
import autocompleteKeys from './autocomplete-keys';
import autocompleteValues from './autocomplete-values';

const reducer = combineReducers({
  spans,
  trace,
  traces,
  services,
  dependencies,
  globalSearch,
  autocompleteKeys,
  autocompleteValues,
});

export default reducer;
