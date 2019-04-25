import { combineReducers } from 'redux';

import remoteServices from './remote-services';
import spans from './spans';
import trace from './trace';
import traces from './traces';
import services from './services';
import dependencies from './dependencies';
import globalSearch from './global-search';
import autocompleteKeys from './autocomplete-keys';
import autocompleteValues from './autocomplete-values';
import traceViewer from './trace-viewer';

const reducer = combineReducers({
  remoteServices,
  spans,
  trace,
  traces,
  services,
  dependencies,
  globalSearch,
  autocompleteKeys,
  autocompleteValues,
  traceViewer,
});

export default reducer;
