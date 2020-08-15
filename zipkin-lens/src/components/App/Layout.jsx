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
import PropTypes from 'prop-types';
import React from 'react';
import styled from 'styled-components';

import LanguageSelector from './LanguageSelector';
import { useUiConfig } from '../UiConfig';
import TraceIdSearchInput from '../Common/TraceIdSearchInput';
import TraceJsonUploader from '../Common/TraceJsonUploader';
import { darkTheme } from '../../colors';
import logoSrc from '../../img/zipkin-logo.png';
import HeaderMenuItem from './HeaderMenuItem';

const propTypes = {
  children: PropTypes.arrayOf(PropTypes.element).isRequired,
};

const Layout = ({ children }) => {
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
                <Box pr={2} color="inherit">
                  <LanguageSelector />
                </Box>
                <TraceJsonUploader />
                <TraceIdSearchInput />
                {config.supportUrl && (
                  <Tooltip title={i18n._(t`Support`)}>
                    <IconButton href={config.supportUrl}>
                      <FontAwesomeIcon icon={faQuestionCircle} />
                    </IconButton>
                  </Tooltip>
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

Layout.propTypes = propTypes;

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

const IconButton = styled(MuiIconButton)`
  margin-right: ${({ theme }) => theme.spacing(2)}px;
  margin-left: ${({ theme }) => theme.spacing(2)}px;
`;
