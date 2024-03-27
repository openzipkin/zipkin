/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */

const localeThemeKey = 'zipkinThemeOverride';

export function getTheme(): string {
  const override = localStorage.getItem(localeThemeKey);
  if (override) {
    return override;
  }
  return 'light';
}

export function setTheme(theme: string) {
  localStorage.setItem(localeThemeKey, theme);
}
