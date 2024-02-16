/*
 * Copyright 2015-2024 The OpenZipkin Authors
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
import { Button, Menu, MenuItem } from '@material-ui/core';
import ExpandMoreIcon from '@material-ui/icons/ExpandMore';
import TranslateIcon from '@material-ui/icons/Translate';
import React, { useCallback, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { FALLBACK_LOCALE } from '../../translations/i18n';

// We want to display all the languages in native language, not current locale, so hard-code the
// strings here instead of using internationalization.
//
// Exported for testing
export const LANGUAGES = [
  {
    locale: 'en',
    name: 'English',
  },
  {
    locale: 'es',
    name: 'Español',
  },
  {
    locale: 'fr',
    name: 'Français',
  },
  {
    locale: 'zh_cn',
    name: '中文 (简体)',
  },
];

const LanguageSelector = () => {
  const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);
  const { i18n } = useTranslation();

  const handleButtonClick = useCallback(
    (event: React.MouseEvent<HTMLButtonElement>) => {
      event.preventDefault();
      setAnchorEl(event.currentTarget);
    },
    [],
  );

  const handleMenuClose = useCallback(() => {
    setAnchorEl(null);
  }, []);

  const handleMenuItemClick = useCallback(
    (event: React.MouseEvent<HTMLLIElement>) => {
      setAnchorEl(null);
      const { locale } = event.currentTarget.dataset;
      if (!locale) {
        return;
      }
      if (locale === i18n.language) {
        return;
      }

      i18n.changeLanguage(locale);
    },
    [i18n],
  );

  useEffect(() => {
    if (LANGUAGES.find((lang) => lang.locale === i18n.language)) {
      i18n.changeLanguage(i18n.language);
    } else {
      i18n.changeLanguage(FALLBACK_LOCALE); // fallback to default language if the selected language is not supported
    }
  }, [i18n]);

  return (
    <>
      <Button
        onClick={handleButtonClick}
        startIcon={<TranslateIcon />}
        endIcon={<ExpandMoreIcon />}
        data-testid="change-language-button"
      >
        {LANGUAGES.find((lang) => lang.locale === i18n.language)?.name}
      </Button>
      <Menu
        anchorEl={anchorEl}
        keepMounted
        open={Boolean(anchorEl)}
        onClose={handleMenuClose}
      >
        {LANGUAGES.map((lang) => (
          <MenuItem
            key={lang.locale}
            onClick={handleMenuItemClick}
            selected={lang.locale === i18n.language}
            data-locale={lang.locale}
            data-testid={`language-list-item-${lang.locale}`}
          >
            {lang.name}
          </MenuItem>
        ))}
      </Menu>
    </>
  );
};

export default LanguageSelector;
