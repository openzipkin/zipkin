/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// The import of 'publicPath' module has to be the first statement in this entry point file
// so that '__webpack_public_path__' (see https://webpack.github.io/docs/configuration.html#output-publicpath)
// is set soon enough.
// In the same time, 'contextRoot' is made available as the context root path reference.
import {contextRoot} from './publicPath';

import {compose, registry, advice, debug} from 'flightjs';
import crossroads from 'crossroads';
import initializeDefault from './page/default';
import initializeTrace from './page/trace';
import initializeTraceViewer from './page/traceViewer';
import initializeDependency from './page/dependency';
import CommonUI from './page/common';
import loadConfig from './config';
import {errToStr} from './component_ui/error';

loadConfig().then(config => {
  debug.enable(true);
  compose.mixin(registry, [advice.withAdvice]);

  CommonUI.attachTo(window.document.body, {config});

  crossroads.addRoute(contextRoot, () => initializeDefault(config));
  crossroads.addRoute(`${contextRoot}traces/{id}`, traceId => initializeTrace(traceId, config));
  crossroads.addRoute(`${contextRoot}traceViewer`, () => initializeTraceViewer(config));
  crossroads.addRoute(`${contextRoot}dependency`, () => initializeDependency(config));
  crossroads.parse(window.location.pathname);
}, e => {
  // TODO: better error message, but this is better than a blank screen...
  const err = errToStr(e);
  document.write(`Error loading config.json: ${err}`);
});
