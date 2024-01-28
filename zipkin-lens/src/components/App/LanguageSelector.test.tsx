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

/*
 * Copyright 2015-2023 The OpenZipkin Authors
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
import { fireEvent, screen } from '@testing-library/react';
import React from 'react';

import render from '../../test/util/render-with-default-settings';
import {describe, it,expect} from 'vitest'
import LanguageSelector from './LanguageSelector';
import i18n from "../../translations/i18n";

describe('<LanguageSelector />', () => {
  it('displays button', async () => {
    render(<LanguageSelector />);
    const changeLanguageButton = screen.getByTestId('change-language-button');
    expect(changeLanguageButton).toBeDefined();
    i18n.language
    expect(i18n.language).toEqual('en');
  });

  it('displays all languages', async () => {
    render(<LanguageSelector />);
    expect(screen.getByTestId('language-list-item-en')).toBeDefined();
    expect(screen.getByTestId('language-list-item-es')).toBeDefined();
    expect(screen.getByTestId('language-list-item-fr')).toBeDefined();
    expect(screen.getByTestId('language-list-item-zh-cn')).toBeDefined();
    expect(i18n.language).toEqual('en');
  });

  it('language select changes locale and refreshes', async () => {
    render(<LanguageSelector />);
    fireEvent.click(screen.getByTestId('language-list-item-zh-cn'));
    expect(i18n.language).toEqual('zh-cn');
  });
});
