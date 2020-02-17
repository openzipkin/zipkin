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
import { faGlobe } from "@fortawesome/free-solid-svg-icons";
import List from '@material-ui/core/List';
import ListItem from '@material-ui/core/ListItem';
import ListItemText from '@material-ui/core/ListItemText';
import Popover from '@material-ui/core/Popover';
import React, { useCallback, useRef, useState } from 'react';
import { useIntl } from 'react-intl';

import { setLocale } from '../../util/locale';

import SidebarMenu from './SidebarMenu';

import messages from './messages';

// We want to display all the languages in native language, not current locale, so hard-code the
// strings here instead of using react-intl.
//
// Exported for testing
export const LANGUAGES = [
  {
    locale: 'en',
    name: 'English',
  },
  {
    locale: 'zh-cn',
    name: '中文 (简体)',
  },
];

const LanguageSelector = () => {
  const changeLanguageLink = useRef(null);
  const intl = useIntl();

  const [languageSelectorOpen, setLanguageSelectorOpen] = useState(false);
  const closeLanguageSelector = useCallback(() => {
    setLanguageSelectorOpen(false);
  }, []);

  const onChangeLanguageClick = useCallback((e) => {
    e.preventDefault();
    setLanguageSelectorOpen(true);
  }, []);

  const currentLocale = intl.locale;

  const onLanguageClick = useCallback((e) => {
    const locale = e.currentTarget.dataset.locale;
    if (locale === currentLocale) {
      return;
    }
    setLocale(locale);
    window.location.reload();
  }, [currentLocale]);

  return (
    <>
      <SidebarMenu
        title={intl.formatMessage(messages.changeLanguage)}
        path=""
        icon={faGlobe}
        ref={changeLanguageLink}
        onClick={onChangeLanguageClick}
        data-testid="change-language-button"
      />
      <Popover
        anchorEl={changeLanguageLink.current}
        open={languageSelectorOpen}
        onClose={closeLanguageSelector}
        anchorOrigin={{vertical: 'top', horizontal: 'right'}}
      >
        <List data-testid="language-list">
          {LANGUAGES.map((language) => (
            <ListItem
              button
              key={language.locale}
              selected={currentLocale === language.locale}
              onClick={onLanguageClick}
              data-locale={language.locale}
              data-testid={`language-list-item-${language.locale}`}
            >
              <ListItemText primary={language.name} />
            </ListItem>
          ))}
        </List>
      </Popover>
    </>
  );
};

export default LanguageSelector;
