/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { IconButton } from '@material-ui/core';
import Brightness7Icon from '@material-ui/icons/Brightness7';
import Brightness4Icon from '@material-ui/icons/Brightness4';
import React, { useCallback, useEffect, useState } from 'react';

import { getTheme, setTheme } from '../../util/theme';

// ThemeSelector Component
// This component allows the user to toggle between light and dark themes by clicking an icon.
// It automatically updates the icon to reflect the current theme (sun for light, moon for dark).
// The component uses local state to track the current theme and updates it based on user interaction.
const ThemeSelector = () => {
  const [currentTheme, setCurrentTheme] = useState('light'); // Default theme set to 'light'

  useEffect(() => {
    const theme = getTheme(); // Retrieves the current theme
    setCurrentTheme(theme); // Updates the state with the current theme
  }, []);

  const toggleTheme = useCallback(() => {
    const newTheme = currentTheme === 'light' ? 'dark' : 'light'; // Toggles between themes
    setTheme(newTheme); // Sets the new theme
    setCurrentTheme(newTheme); // Updates the state with the new theme
    window.location.reload(); // Reloads the page to apply the theme change
  }, [currentTheme]);

  return (
    <>
      <IconButton onClick={toggleTheme} color="inherit">
        {currentTheme === 'light' ? <Brightness7Icon /> : <Brightness4Icon />}
      </IconButton>
    </>
  );
};

export default ThemeSelector;
