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
import PropTypes from 'prop-types';
import React from 'react';
import { faSearch, faProjectDiagram, faHome } from '@fortawesome/free-solid-svg-icons';
import { faGithub, faTwitter, faGitter } from '@fortawesome/free-brands-svg-icons';
import Box from '@material-ui/core/Box';
import Drawer from '@material-ui/core/Drawer';
import CssBaseline from '@material-ui/core/CssBaseline';
import List from '@material-ui/core/List';
import { makeStyles } from '@material-ui/styles';

import LanguageSelector from './LanguageSelector';
import SidebarMenu from './SidebarMenu';
import Logo from '../../img/zipkin-logo.svg';

const drawerWidth = '3.2rem';

const useStyles = makeStyles(theme => ({
  root: {
    display: 'flex',
  },
  drawer: {
    width: drawerWidth,
    flexShring: 0,
  },
  drawerPaper: {
    width: drawerWidth,
    backgroundColor: theme.palette.grey[900],
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'space-between',
  },
  zipkinLogoWrapper: {
    display: 'flex',
    justifyContent: 'center',
    width: '100%',
  },
  zipkinLogo: {
    marginTop: theme.spacing(1),
    marginBottom: theme.spacing(0.5),
    width: '2.2rem',
    height: '2.2rem',
    '& *': {
      fill: theme.palette.common.white,
    },
  },
  childrenWrapper: {
    width: '100%',
    height: '100vh',
    overflow: 'auto',
  },
}));

const propTypes = {
  children: PropTypes.arrayOf(PropTypes.element).isRequired,
};

const Layout = ({ children }) => {
  const classes = useStyles();

  return (
    <Box className={classes.root}>
      <CssBaseline />
      <Drawer open variant="permanent" className={classes.drawer} classes={{ paper: classes.drawerPaper }}>
        <Box>
          <Box className={classes.zipkinLogoWrapper}>
            <Logo className={classes.zipkinLogo} />
          </Box>
          <List>
            <SidebarMenu title="Discover Page" path="/" icon={faSearch} />
            <SidebarMenu title="Dependencies Page" path="/dependency" icon={faProjectDiagram} />
          </List>
        </Box>
        <List>
          <SidebarMenu title="Zipkin Home" path="https://zipkin.io/" icon={faHome} />
          <SidebarMenu title="Repository" path="https://github.com/openzipkin/zipkin" icon={faGithub} />
          <SidebarMenu title="Twitter" path="https://twitter.com/zipkinproject" icon={faTwitter} />
          <SidebarMenu title="Gitter" path="https://gitter.im/openzipkin/zipkin/" icon={faGitter} />
          <LanguageSelector />
        </List>
      </Drawer>
      <Box component="main" className={classes.childrenWrapper}>
        {children}
      </Box>
    </Box>
  );
};


Layout.propTypes = propTypes;

export default Layout;
