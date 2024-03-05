/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { createTheme } from '@material-ui/core/styles';
import * as colors from '@material-ui/core/colors';

export const primaryColor = '#6a9fb5';

export const theme = createTheme({
  palette: {
    primary: {
      main: primaryColor,
      contrastText: '#fff',
    },
  },
});

export const darkTheme = createTheme({
  palette: {
    type: 'dark',
    primary: {
      main: primaryColor,
      contrastText: '#fff',
    },
  },
});

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
  return allColorThemes[hash % allColors.length];
};

export const selectServiceColor = (serviceName: string) =>
  selectServiceTheme(serviceName).palette.primary.dark;

export const selectColorByErrorType = (errorType: string) => {
  switch (errorType) {
    case 'transient':
      return colors.red[500];
    case 'critical':
      return colors.red[500];
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
