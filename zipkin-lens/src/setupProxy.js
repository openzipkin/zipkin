/*
 * Copyright 2015-2020 The OpenZipkin Authors
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

const { createProxyMiddleware } = require('http-proxy-middleware');

const API_BASE = process.env.API_BASE || 'http://localhost:9411';

// Default create-react-app proxy only proxies AJAX requests by looking at Accept headers. We want
// to proxy any request though, mainly to support the Download JSON button.
const proxy = createProxyMiddleware(['**/api/**', '**/config.json'], {
  target: API_BASE,
  changeOrigin: true,
});

module.exports = (app) => {
  app.use('/', proxy);
};
