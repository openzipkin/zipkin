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
import $ from 'jquery';

// read the public path from the <base> tag where it has to be set anyway because of
// html-webpack-plugin limitations: https://github.com/jantimon/html-webpack-plugin/issues/119
// otherwise it could be: window.location.pathname.replace(/(.*)\/zipkin\/.*/, '$1/zipkin/')
let contextRoot = $('base').attr('href') || '/zipkin/';

// explicit to avoid having to do a polyfill for String.endsWith
if (contextRoot.substr(contextRoot.length - 1) !== '/') {
  contextRoot += '/';
}

// set dynamically 'output.publicPath' as per https://webpack.github.io/docs/configuration.html#output-publicpath
__webpack_public_path__ = contextRoot; // eslint-disable-line camelcase, no-undef

export {contextRoot};
