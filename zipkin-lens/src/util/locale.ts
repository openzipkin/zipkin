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

import { setupI18n } from '@lingui/core';
import { messages as enMessages } from '../translations/en/messages';
import { messages as esMessages } from '../translations/es/messages';
import { messages as frMessages } from '../translations/fr/messages';
import { messages as zhCnMessages } from '../translations/zh-cn/messages';

const localeStorageKey = 'localeOverride';

const allMessages = {
  en: enMessages,
  es: esMessages,
  fr: frMessages,
  'zh-cn': zhCnMessages,
};

export function getLocale(): string {
  const override = localStorage.getItem(localeStorageKey);
  if (override) {
    return override;
  }

  const browserLanguage = navigator.language.toLowerCase();

  if (Object.prototype.hasOwnProperty.call(allMessages, browserLanguage)) {
    return browserLanguage;
  }

  const hyphenIndex = browserLanguage.indexOf('-');
  if (hyphenIndex >= 0) {
    const stripped = browserLanguage.substring(0, hyphenIndex);
    if (Object.prototype.hasOwnProperty.call(allMessages, stripped)) {
      return stripped;
    }
  }

  // Default to English if we don't support the current browser language.
  return 'en';
}

export function setLocale(locale: string) {
  localStorage.setItem(localeStorageKey, locale);
}

export const i18n = setupI18n({
  messages: allMessages as any,
  locale: getLocale(),
  localeData: {
    en: {},
    es: {},
    fr: {},
    zh: {},
  },
});
