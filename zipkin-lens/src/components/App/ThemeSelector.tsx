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
import { Button, Menu, MenuItem } from '@material-ui/core';
import ExpandMoreIcon from '@material-ui/icons/ExpandMore';
import PaletteIcon from '@material-ui/icons/Palette';
import React, { useCallback } from 'react';
import {THEME} from '../../constants/color';

import { getTheme, setTheme } from '../../util/locale';

// We want to display all the languages in native language, not current locale, so hard-code the
// strings here instead of using internationalization.
//
// Exported for testing

const ThemeSelector = () => {
  const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);

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

  const currentThemeName = getTheme();

  const handleMenuItemClick = useCallback(
    (event: React.MouseEvent<HTMLLIElement>) => {
      setAnchorEl(null);
      const { theme } = event.currentTarget.dataset;
      if (!theme) {
        return;
      }
      if (theme === currentThemeName) {
        return;
      }
      setTheme(theme);
      window.location.reload();
    },
    [currentThemeName],
  );

  return (
    <>
      <Button
        onClick={handleButtonClick}
        startIcon={<PaletteIcon />}
        endIcon={<ExpandMoreIcon />}
        data-testid="change-language-button"
      >
        {THEME.find((theme) => theme.name === currentThemeName)?.label}
      </Button>
      <Menu
        anchorEl={anchorEl}
        keepMounted
        open={Boolean(anchorEl)}
        onClose={handleMenuClose}
      >
        {THEME.map((theme) => (
          <MenuItem
            key={theme.name}
            onClick={handleMenuItemClick}
            data-theme={theme.name}
          >
            {theme.label}
          </MenuItem>
        ))}
      </Menu>
    </>
  );
};

export default ThemeSelector;
