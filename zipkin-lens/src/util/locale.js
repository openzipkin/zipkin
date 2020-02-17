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

const localeStorageKey = 'localeOverride';

export const DEFAULT_LOCALE = 'en';

export function getLocale() {
  const override = localStorage.getItem(localeStorageKey);
  if (override) {
    return override;
  }
  const browserLanguage = navigator.language.toLowerCase();
  // Strip browser language to what we support.
  if (browserLanguage === 'en' || browserLanguage.startsWith('en-')) {
    return 'en';
  }
  if (browserLanguage === 'zh-cn') {
    return 'zh-cn';
  }

  return browserLanguage;
}

export function setLocale(locale) {
  localStorage.setItem(localeStorageKey, locale);
}
