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
import {
  faProjectDiagram,
  faSearch,
  faQuestionCircle,
} from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { t } from '@lingui/macro';
import { useLingui } from '@lingui/react';
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

import HeaderMenuItem from './HeaderMenuItem';
import LanguageSelector from './LanguageSelector';
import TraceIdSearch from './TraceIdSearch';
import TraceJsonUploader from './TraceJsonUploader';
import { useUiConfig } from '../UiConfig';
import { darkTheme } from '../../constants/color';
import logoSrc from '../../img/zipkin-logo.png';

const Layout: React.FC = ({ children }) => {
  const { i18n } = useLingui();
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
                <Logo alt={i18n._(t`Zipkin`)} />
              </Box>
              <Title>
                <strong>Zipkin</strong>
              </Title>
              <Box display="flex" ml={3}>
                <HeaderMenuItem
                  title={i18n._(t`Find a trace`)}
                  path="/"
                  icon={faSearch}
                />
                {config.dependency.enabled && (
                  <HeaderMenuItem
                    title={i18n._(t`Dependencies`)}
                    path="/dependency"
                    icon={faProjectDiagram}
                  />
                )}
              </Box>
            </Box>
            <ThemeProvider theme={darkTheme}>
              <Box display="flex" alignItems="center">
                <LanguageSelector />
                <Box mr={2} ml={2}>
                  <TraceJsonUploader />
                </Box>
                <TraceIdSearch />
                {config.supportUrl && (
                  <Box ml={1}>
                    <Tooltip title={i18n._(t`Support`)}>
                      <MuiIconButton href={config.supportUrl}>
                        <FontAwesomeIcon icon={faQuestionCircle} />
                      </MuiIconButton>
                    </Tooltip>
                  </Box>
                )}
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
  src: logoSrc,
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
