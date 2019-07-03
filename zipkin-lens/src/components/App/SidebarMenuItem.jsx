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
import PropTypes from 'prop-types';
import React from 'react';
import { withRouter } from 'react-router';
import { makeStyles } from '@material-ui/styles';
import Box from '@material-ui/core/Box';
import ListItem from '@material-ui/core/ListItem';
import Tooltip from '@material-ui/core/Tooltip';

import { theme } from '../../colors';

const useStyles = makeStyles({
  item: {
    height: '3.2rem',
    cursor: 'pointer',
    fontSize: '1.05rem',
    color: theme.palette.grey[400],
    '&:hover': {
      color: theme.palette.common.white,
    },
  },
});

const propTypes = {
  history: PropTypes.shape({ push: PropTypes.func.isRequired }).isRequired,
  location: PropTypes.shape({ pathname: PropTypes.string.isRequired }).isRequired,
  isExternalLink: PropTypes.bool,
  title: PropTypes.string.isRequired,
  url: PropTypes.string.isRequired,
  buttonClassName: PropTypes.string.isRequired,
};

const defaultProps = {
  isExternalLink: false,
};

const SidebarMenuItem = ({
  history,
  location,
  isExternalLink,
  title,
  url,
  buttonClassName,
}) => {
  const classes = useStyles();

  const style = location.pathname === url
    ? {
      color: theme.palette.common.white,
      backgroundColor: theme.palette.primary.dark,
    }
    : null;

  const props = { button: true, style, className: classes.item };

  if (isExternalLink) {
    props.component = 'a';
    props.href = url;
  } else {
    props.onClick = () => history.push(url);
  }

  return (
    <Tooltip title={title} placement="right">
      <ListItem {...props}>
        <Box component="span" className={buttonClassName} />
      </ListItem>
    </Tooltip>
  );
};

SidebarMenuItem.propTypes = propTypes;
SidebarMenuItem.defaultProps = defaultProps;

export default withRouter(SidebarMenuItem);
