import { combineReducers } from 'redux';

import spans from './spans';
import trace from './trace';
import traces from './traces';
import services from './services';
import dependencies from './dependencies';

const reducer = combineReducers({
  spans,
  trace,
  traces,
  services,
  dependencies,
});

export default reducer;
