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

// It's not clear what are possible values for navigator.language, so we try a bunch for good
// measure.

const setLanguageForTest = (language: string) => {
  Object.defineProperty(window.navigator, 'language', {
    value: language,
    configurable: true,
  });
};

describe('getting locale', () => {
  it('browser language es-AR sets locale es', () => {
    setLanguageForTest('es-AR');
    expect(getLocale()).toEqual('es');
  });

  it('browser language lower case works the same as mixed case', () => {
    setLanguageForTest('es-ar');
    expect(getLocale()).toEqual('es');
  });

  it('browser language es sets locale es', () => {
    setLanguageForTest('es');
    expect(getLocale()).toEqual('es');
  });

  it('browser language fr-BE sets locale fr', () => {
    setLanguageForTest('fr-BE');
    expect(getLocale()).toEqual('fr');
  });

  it('browser language fr sets locale fr', () => {
    setLanguageForTest('fr');
    expect(getLocale()).toEqual('fr');
  });

  it('browser language zh-CN sets locale zh-cn', () => {
    setLanguageForTest('zh-CN');
    expect(getLocale()).toEqual('zh-cn');
  });

  it('unsupported variant defaults to en', () => {
    setLanguageForTest('zh-hk');
    expect(getLocale()).toEqual('en');
  });

  it('unknown locale defaults to en', () => {
    setLanguageForTest('ja-jp');
    expect(getLocale()).toEqual('en');
  });

  it('setLocale overrides browser language', () => {
    setLanguageForTest('en-US');
    setLocale('ja-jp');
    expect(getLocale()).toEqual('ja-jp');
  });
});
