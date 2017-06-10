/*
 * Copyright 2015-2017 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
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

  crossroads.addRoute('', () => initializeDefault(config));
  crossroads.addRoute('traces/{id}', traceId => initializeTrace(traceId, config));
  crossroads.addRoute('dependency', () => initializeDependency(config));
  crossroads.parse(window.location.pathname);
}, e => {
  // TODO: better error message, but this is better than a blank screen...
  const err = errToStr(e);
  document.write(`Error loading config.json: ${err}`);
});
