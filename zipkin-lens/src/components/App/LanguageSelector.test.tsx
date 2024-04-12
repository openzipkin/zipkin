/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import { fireEvent, screen } from '@testing-library/react';
import React from 'react';

import render from '../../test/util/render-with-default-settings';
import { describe, it, expect } from 'vitest';
import LanguageSelector from './LanguageSelector';
import i18n from '../../translations/i18n';

describe('<LanguageSelector />', () => {
  it('displays button', async () => {
    render(<LanguageSelector />);
    const changeLanguageButton = screen.getByTestId('change-language-button');
    expect(changeLanguageButton).toBeDefined();
    i18n.language;
    expect(i18n.language).toEqual('en');
  });

  it('displays all languages', async () => {
    render(<LanguageSelector />);
    expect(screen.getAllByTestId('language-list-item-en')).toBeDefined();
    expect(screen.getAllByTestId('language-list-item-es')).toBeDefined();
    expect(screen.getAllByTestId('language-list-item-fr')).toBeDefined();
    expect(screen.getAllByTestId('language-list-item-zh_cn')).toBeDefined();
    expect(i18n.language).toEqual('en');
  });

  it('language select changes locale and refreshes', async () => {
    render(<LanguageSelector />);
    fireEvent.click(screen.getAllByTestId('language-list-item-zh_cn')[0]);
    expect(i18n.language).toEqual('zh_cn');
  });
});
