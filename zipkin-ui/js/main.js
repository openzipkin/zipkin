// read the public path from the <base> tag where it has to be set anyway because of
// html-webpack-plugin limitations: https://github.com/jantimon/html-webpack-plugin/issues/119
// otherwise it could be: window.location.pathname.replace(/(.*)\/zipkin\/.*/, '$1/zipkin/')
__webpack_public_path__ = $('base').attr('href'); // eslint-disable-line camelcase, no-undef

import {compose, registry, advice, debug} from 'flightjs';
import crossroads from 'crossroads';
import initializeDefault from './page/default';
import initializeTrace from './page/trace';
import initializeDependency from './page/dependency';
import CommonUI from './page/common';
import loadConfig from './config';
import {errToStr} from './component_ui/error';

loadConfig().then(config => {
  debug.enable(true);
  compose.mixin(registry, [advice.withAdvice]);

  CommonUI.attachTo(window.document.body, {config});

  const root = __webpack_public_path__; // eslint-disable-line camelcase, no-undef
  crossroads.addRoute(root, () => initializeDefault(config));
  crossroads.addRoute(`${root}traces/{id}`, traceId => initializeTrace(traceId, config));
  crossroads.addRoute(`${root}dependency`, () => initializeDependency(config));
  crossroads.parse(window.location.pathname);
}, e => {
  // TODO: better error message, but this is better than a blank screen...
  const err = errToStr(e);
  document.write(`Error loading config.json: ${err}`);
});
