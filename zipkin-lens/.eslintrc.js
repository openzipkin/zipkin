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
  extends: [
    'airbnb-typescript',
    'plugin:prettier/recommended',
    'prettier/@typescript-eslint',
    'prettier/react',
  ],
  rules: {
    'import/prefer-default-export': 'off',
    'react/jsx-filename-extension': [1, { extensions: ['.js', '.jsx', '.tsx'] }],
    'react/jsx-props-no-spreading': 'off',
    'react/no-array-index-key': 'off',
    'react-hooks/rules-of-hooks': 'error',
    'react-hooks/exhaustive-deps': 'warn',
    'react/no-unescaped-entities': 'off',
    'max-classes-per-file': 'off',
    'no-console': 0,
    'no-underscore-dangle': ['off'],
    'no-continue': ['off'],
    'prefer-destructuring': ['error', {
      'AssignmentExpression': {array: false, object: false},
      'VariableDeclarator': {array: true, object: true}
    }]
  },
  parser: '@typescript-eslint/parser',
  parserOptions: {
    tsconfigRootDir: __dirname,
    project: ['./tsconfig.json']
  },
  plugins: [
    '@typescript-eslint',
    'jest',
    'react-hooks'
  ],
  globals: {
    fetch: false,
    document: false
  },
  env: {
    browser: true,
    jquery: true,
    'jest/globals': true
  },
  overrides: [
    {
      files: ['**/*.tsx'],
      rules: {
        'react/prop-types': 'off'
      }
    }
  ]
};
