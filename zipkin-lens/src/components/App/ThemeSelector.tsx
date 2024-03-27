/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { Button, Menu, MenuItem } from '@material-ui/core';
import ExpandMoreIcon from '@material-ui/icons/ExpandMore';
import PaletteIcon from '@material-ui/icons/Palette';
import React, { useCallback } from 'react';
import { THEME } from '../../constants/color';

import { getTheme, setTheme } from '../../util/theme';

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
