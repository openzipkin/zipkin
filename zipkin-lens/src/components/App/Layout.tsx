/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  faProjectDiagram,
  faSearch,
  faQuestionCircle,
} from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  AppBar as MuiAppBar,
  Box,
  CssBaseline,
  ThemeProvider,
  Toolbar as MuiToolbar,
  Typography,
  IconButton as MuiIconButton,
  Tooltip,
} from '@material-ui/core';
import React from 'react';
import styled from 'styled-components';

import { useTranslation } from 'react-i18next';
import HeaderMenuItem from './HeaderMenuItem';
import LanguageSelector from './LanguageSelector';
import ThemeSelector from './ThemeSelector';
import TraceIdSearch from './TraceIdSearch';
import TraceJsonUploader from './TraceJsonUploader';
import { useUiConfig } from '../UiConfig';
import { darkTheme } from '../../constants/color';

const Layout: React.FC = ({ children }) => {
  const { t } = useTranslation();
  const config = useUiConfig();

  return (
    <Box display="flex">
      <CssBaseline />
      <AppBar>
        <Toolbar>
          <Box
            width="100%"
            display="flex"
            justifyContent="space-between"
            alignItems="center"
          >
            <Box display="flex" alignItems="center">
              <Box
                width={64}
                height={64}
                display="flex"
                justifyContent="center"
                alignItems="center"
              >
                <Logo alt={t(`Zipkin`).toString()} />
              </Box>
              <Title>
                <strong>Zipkin</strong>
              </Title>
              <Box display="flex" ml={3}>
                <HeaderMenuItem
                  title={t(`Find a trace`)}
                  path="/"
                  icon={faSearch}
                />
                {config.dependency.enabled && (
                  <HeaderMenuItem
                    title={t(`Dependencies`)}
                    path="/dependency"
                    icon={faProjectDiagram}
                  />
                )}
              </Box>
            </Box>
            <ThemeProvider theme={darkTheme}>
              <Box display="flex" alignItems="center">
                <Box mr={2} ml={2}>
                  <TraceJsonUploader />
                </Box>
                <TraceIdSearch />
                {config.supportUrl && (
                  <Box ml={1}>
                    <Tooltip title={t(`Support`).toString()}>
                      <MuiIconButton href={config.supportUrl}>
                        <FontAwesomeIcon icon={faQuestionCircle} />
                      </MuiIconButton>
                    </Tooltip>
                  </Box>
                )}
                <Box pl={2} ml={2} mr={2} borderLeft={1} borderColor={'#FFF'}>
                  <LanguageSelector />
                </Box>
                <ThemeSelector />
              </Box>
            </ThemeProvider>
          </Box>
        </Toolbar>
      </AppBar>
      <Box component="main" width="100%">
        <ToolbarSpace />
        {children}
      </Box>
    </Box>
  );
};

export default Layout;

const AppBar = styled(MuiAppBar).attrs({
  position: 'fixed',
})`
  background-color: ${({ theme }) => theme.palette.grey[800]};
  z-index: 1300;
`;

const Toolbar = styled(MuiToolbar)`
  padding-left: 0;
`;

const Logo = styled.img.attrs({
  src: './static/media/zipkin-logo.png',
})`
  width: 42px;
  height: 42px;
`;

const Title = styled(Typography).attrs({
  variant: 'h5',
})`
  margin-left: ${({ theme }) => theme.spacing(2)}px;
  margin-right: ${({ theme }) => theme.spacing(2)}px;
`;

const ToolbarSpace = styled.div`
  min-height: 64px;
`;
