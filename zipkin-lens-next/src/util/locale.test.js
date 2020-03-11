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

import { getLocale, setLocale } from './locale';

// It's not clear what are possible values for navigator.language, so we test a bunch for good
// measure.

const setLanguageForTest = (language) => {
  Object.defineProperty(window.navigator, 'language', { value: language, configurable: true });
};

test('browser language en-US sets locale en', () => {
  setLanguageForTest('en-US');
  expect(getLocale()).toEqual('en');
});

test('browser language lower case works the same as mixed case', () => {
  setLanguageForTest('en-us');
  expect(getLocale()).toEqual('en');
});

test('browser language en sets locale en', () => {
  setLanguageForTest('en');
  expect(getLocale()).toEqual('en');
});

test('browser language zh-CN sets locale zh-cn', () => {
  setLanguageForTest('zh-CN');
  expect(getLocale()).toEqual('zh-cn');
});

test('unknown locale returned as is', () => {
  setLanguageForTest('ja-jp');
  expect(getLocale()).toEqual('ja-jp');
});

test('setLocale overrides browser language', () => {
  setLanguageForTest('en-US');
  setLocale('ja-jp');
  expect(getLocale()).toEqual('ja-jp');
});
