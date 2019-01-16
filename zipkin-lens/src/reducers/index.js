import { combineReducers } from 'redux';

import spans from './spans';
import trace from './trace';
import traces from './traces';
import services from './services';
import dependencies from './dependencies';
import globalSearch from './global-search';

const reducer = combineReducers({
  spans,
  trace,
  traces,
  services,
  dependencies,
  globalSearch,
});

export default reducer;
