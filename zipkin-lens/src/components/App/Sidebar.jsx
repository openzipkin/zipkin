/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
import React from 'react';
import { faSearch, faProjectDiagram, faHome } from '@fortawesome/free-solid-svg-icons';
import { faGithub, faTwitter, faGitter } from '@fortawesome/free-brands-svg-icons';
import { makeStyles } from '@material-ui/styles';
import Box from '@material-ui/core/Box';
import Drawer from '@material-ui/core/Drawer';
import List from '@material-ui/core/List';
import grey from '@material-ui/core/colors/grey';

import SidebarMenuItem from './SidebarMenuItem';
import { theme } from '../../colors';
import Logo from '../../img/zipkin-logo.svg';

const useStyles = makeStyles({
  paper: {
    width: '3.2rem',
    backgroundColor: grey[900],
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
    marginTop: '0.8rem',
    marginBottom: '0.35rem',
    width: '2.2rem',
    height: '2.2rem',
    '& *': {
      fill: theme.palette.common.white,
    },
  },
});

const Sidebar = () => {
  const classes = useStyles();

  return (
    <Drawer open variant="permanent" classes={{ paper: classes.paper }}>
      <Box>
        <Box className={classes.zipkinLogoWrapper}>
          <Logo className={classes.zipkinLogo} />
        </Box>
        <List>
          <SidebarMenuItem title="Discover Page" path="/zipkin" icon={faSearch} />
          <SidebarMenuItem title="Dependencies Page" path="/zipkin/dependency" icon={faProjectDiagram} />
        </List>
      </Box>
      <List>
        <SidebarMenuItem title="Zipkin Home" path="https://zipkin.io/" icon={faHome} />
        <SidebarMenuItem title="Repository" path="https://github.com/openzipkin/zipkin" icon={faGithub} />
        <SidebarMenuItem title="Twitter" path="https://twitter.com/zipkinproject" icon={faTwitter} />
        <SidebarMenuItem title="Gitter" path="https://gitter.im/openzipkin/zipkin/" icon={faGitter} />
      </List>
    </Drawer>
  );
};

export default Sidebar;
