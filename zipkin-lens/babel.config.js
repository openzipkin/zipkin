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

module.exports = api => {
  const isTest = api.env('test');
  const targets = isTest ? { node: 'current' } : [
    'last 2 edge version',
    'last 2 firefox version',
    'last 2 chrome version',
    'last 2 safari version',
  ];
  const presets = [
    [
      '@babel/env',
      {
        modules: isTest ? 'commonjs' : false,
        useBuiltIns: 'usage',
        corejs: 3,
        targets,
      },
    ],
    '@babel/react',
  ];

  const plugins = [
    [
      '@babel/plugin-transform-runtime',
      {
        corejs: 3,
        useESModules: !isTest,
      },
    ],
    'babel-plugin-react-intl-auto',
  ];
  return {
    presets,
    plugins,
  };
};
