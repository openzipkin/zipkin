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
module.exports = {
  extends: ['plugin:prettier/recommended', 'react-app', 'react-app/jest'],
  rules: {
    'import/no-anonymous-default-export': 'off',
    'import/prefer-default-export': 'off',
    'max-classes-per-file': 'off',
    'no-console': 0,
    'no-underscore-dangle': ['off'],
    'no-continue': ['off'],
    'prefer-destructuring': [
      'error',
      {
        AssignmentExpression: { array: false, object: false },
        VariableDeclarator: { array: true, object: true },
      },
    ],
    'react/jsx-filename-extension': [
      1,
      { extensions: ['.js', '.jsx', '.tsx'] },
    ],
    'react/jsx-props-no-spreading': 'off',
    'react/no-array-index-key': 'off',
    'react/no-unescaped-entities': 'off',
    'react-hooks/exhaustive-deps': 'warn',
  },
  globals: {
    fetch: false,
    document: false,
  },
  env: {
    browser: true,
    jquery: true,
  },
  overrides: [
    {
      files: ['**/*.tsx'],
      rules: {
        'react/prop-types': 'off',
      },
    },
  ],
};
