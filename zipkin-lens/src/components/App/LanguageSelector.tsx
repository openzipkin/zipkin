/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
    iso6391: 'EN',
  },
  {
    locale: 'es',
    name: 'Español',
    iso6391: 'ES',
  },
  {
    locale: 'fr',
    name: 'Français',
    iso6391: 'FR',
  },
  {
    locale: 'zh_cn',
    name: '中文 (简体)',
    iso6391: 'ZH',
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
        {LANGUAGES.find((lang) => lang.locale === i18n.language)?.iso6391}
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
