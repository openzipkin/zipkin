/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { createTheme } from '@material-ui/core/styles';
import * as colors from '@material-ui/core/colors';
import { getTheme } from '../util/theme';

export const primaryColor = '#005B8A';
export const secondaryColor = '#c8001d';
export const errorColor = '#c8001d';

export const THEME = [
  {
    theme: createTheme({
      palette: {
        type: 'light',
        primary: {
          main: primaryColor,
          contrastText: '#fff',
        },
        secondary: {
          main: secondaryColor,
        },
        error: {
          main: errorColor,
          contrastText: '#ffffff',
        },
      },
    }),
    name: 'light',
    label: 'Light',
    servicePalette: [
      '#f44336',
      '#e91e63',
      '#9c27b0',
      '#673ab7',
      '#3f51b5',
      '#2196f3',
      '#03a9f4',
      '#00bcd4',
      '#009688',
      '#4caf50',
      '#8bc34a',
      '#cddc39',
      '#ffeb3b',
      '#ffc107',
      '#ff9800',
      '#ff5722',
      '#795548',
      '#9e9e9e',
      '#607d8b',
    ],
  },
  {
    name: 'dark',
    label: 'Dark',
    servicePalette: [
      '#f44336',
      '#e91e63',
      '#9c27b0',
      '#673ab7',
      '#3f51b5',
      '#2196f3',
      '#03a9f4',
      '#00bcd4',
      '#009688',
      '#4caf50',
      '#8bc34a',
      '#cddc39',
      '#ffeb3b',
      '#ffc107',
      '#ff9800',
      '#ff5722',
      '#795548',
      '#9e9e9e',
      '#607d8b',
    ],
    theme: createTheme({
      palette: {
        type: 'dark',
        primary: {
          main: '#1984BB',
          contrastText: '#fff',
        },
        secondary: {
          main: '#f50024',
          contrastText: '#ffffff',
        },
        error: {
          main: '#f50024',
          contrastText: '#ffffff',
        },
        background: {
          paper: '#424242',
        },
        grey: {
          /*
             Note: Gray colors with shades 50 and 100 are initially used as background colors
             in certain sections. However, due to the specific theme requirements,
             these are overridden with darker shades for better visual compatibility
             and theme coherence.
          */
          50: '#4c4c4c',
          100: '#424242',
        },
      },
    }),
  },
];

export const darkTheme = createTheme({
  palette: {
    type: 'dark',
    primary: {
      main: primaryColor,
      contrastText: '#fff',
    },
    error: {
      main: '#f50024',
      contrastText: '#ffffff',
    },
  },
});

function getCurrentTheme() {
  const currentThemeName = getTheme();
  const currentTheme = THEME.find((x) => x.name === currentThemeName);
  console.log(`${currentTheme?.name} => ${currentThemeName}`);
  if (currentTheme) {
    return currentTheme.theme;
  }
  return THEME[0].theme;
}

function getCurrentThemeServicePalette() {
  const currentThemeName = getTheme();
  const currentTheme = THEME.find((x) => x.name === currentThemeName);
  if (currentTheme) {
    return currentTheme.servicePalette;
  }
  return THEME[0].servicePalette;
}

export const theme = getCurrentTheme();

export const allColors = [
  colors.red,
  colors.pink,
  colors.purple,
  colors.deepPurple,
  colors.indigo,
  colors.blue,
  colors.lightBlue,
  colors.cyan,
  colors.teal,
  colors.green,
  colors.lightGreen,
  colors.lime,
  colors.yellow,
  colors.amber,
  colors.orange,
  colors.deepOrange,
  colors.brown,
  colors.grey,
  colors.blueGrey,
];

export const allColorThemes = allColors.map((color) =>
  createTheme({
    palette: {
      primary: {
        main: color[500],
      },
    },
  }),
);

/* eslint no-bitwise: ["error", { "allow": ["<<", "|="] }] */
const generateHash = (str: string) => {
  let hash = 0;
  if (str.length === 0) return hash;
  for (let i = 0; i < str.length; i += 1) {
    const c = str.charCodeAt(i);
    hash = (hash << 5) - hash + c;
    hash |= 0; // Convert to 32bit integer
  }
  return Math.abs(hash); // Only positive number.
};

export const selectServiceTheme = (serviceName: string) => {
  const hash = generateHash(serviceName);
  const selectedServicePalette = getCurrentThemeServicePalette();
  const themePalette = selectedServicePalette.map((color) =>
    createTheme({
      palette: {
        primary: {
          main: color,
        },
      },
    }),
  );
  return themePalette[hash % selectedServicePalette.length];
};

export const selectServiceColor = (serviceName: string) =>
  selectServiceTheme(serviceName).palette.primary.dark;

export const selectColorByErrorType = (errorType: string) => {
  switch (errorType) {
    case 'transient':
      return theme.palette.error.main;
    case 'critical':
      return theme.palette.error.main;
    default:
      return theme.palette.primary.main;
  }
};

export const selectColorByInfoClass = (infoClass: string) => {
  switch (infoClass) {
    case 'trace-error-transient':
      return selectColorByErrorType('transient');
    case 'trace-error-critical':
      return selectColorByErrorType('critical');
    default:
      return selectColorByErrorType('none');
  }
};
