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
import {createMuiTheme} from '@material-ui/core/styles';
import * as colors from '@material-ui/core/colors';
import {getTheme} from '../util/locale';

export const primaryColor = '#6a9fb5';


export const THEME = [
  {
    theme: createMuiTheme({
      palette: {
        type: 'light',
        primary: {
          main: primaryColor,
          contrastText: '#fff',
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
    theme: createMuiTheme({
      palette: {
        type: 'dark',
        primary: {
          main: '#dddddd',
          contrastText: '#000000',
        },
        secondary: {
          main: '#f50024',
          contrastText: '#ffffff',
        },
      },
    }),
  },
  {
    name: 'protanopia',
    label: 'Protanopia',
    servicePalette: [
      '#0000FF',
      '#008000',
      '#ADD8E6',
      '#FFFF00',
      '#FFB6C1',
      '#8A2BE2',
      '#00CED1',
      '#006400',
      '#FFA500',
      '#808080',
    ],
    theme: createMuiTheme({
      palette: {
        type: 'light',
        primary: {
          main: primaryColor,
          dark: '#abcdff',
          contrastText: '#fffff',
        },
        secondary: {
          main: '#ffe5e5',
          dark: '#ffca02',
        },
      },
    }),
  },
  {
    name: 'tritanopia',
    label: 'Tritanopia',
    servicePalette: [
      '#0000FF',
      '#FF0000',
      '#ADD8E6',
      '#FFFF00',
      '#FFB6C1',
      '#800080',
      '#00CED1',
      '#A52A2A',
      '#FFA500',
      '#808080',
    ],
    theme: createMuiTheme({
      palette: {
        type: 'light',
        primary: {
          main: primaryColor,
          dark: '#abcdff',
          contrastText: '#fffff',
        },
        secondary: {
          main: '#ffe5e5',
          dark: '#ffca02',
        },
      },
    }),
  },
  {
    name: 'deuteranopia',
    label: 'Deuteranopia',
    servicePalette: [
      '#FF0000',
      '#008000',
      '#800080',
      '#FFFF00',
      '#00CED1',
      '#FF69B4',
      '#A52A2A',
      '#00008B',
      '#808080',
      '#FFA500',
    ],
    theme: createMuiTheme({
      palette: {
        type: 'light',
        primary: {
          main: primaryColor,
          dark: '#abcdff',
          contrastText: '#fffff',
        },
        secondary: {
          main: '#ffe5e5',
          dark: '#ffca02',
        },
      },
    }),
  },
];

export const darkTheme = createMuiTheme({
  palette: {
    type: 'dark',
    primary: {
      main: primaryColor,
      contrastText: '#fff',
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
    createMuiTheme({
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
