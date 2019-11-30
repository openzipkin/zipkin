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
import classNames from 'classnames';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { makeStyles } from '@material-ui/styles';
import ListItem from '@material-ui/core/ListItem';
import Tooltip from '@material-ui/core/Tooltip';

const propTypes = {
  location: PropTypes.shape({ pathname: PropTypes.string.isRequired }).isRequired,
  title: PropTypes.string.isRequired,
  path: PropTypes.string.isRequired,
  icon: PropTypes.shape({}).isRequired,
};

const useStyles = makeStyles(theme => ({
  item: {
    height: '3.2rem',
    cursor: 'pointer',
    fontSize: '1.05rem',
    color: theme.palette.grey[400],
    '&:hover': {
      color: theme.palette.common.white,
      backgroundColor: theme.palette.primary.dark,
    },
  },
  'item--selected': {
    color: theme.palette.common.white,
    backgroundColor: theme.palette.primary.dark,
  },
}));

export const SidebarMenuItemImpl = ({
  location,
  title,
  path,
  icon,
}) => {
  const classes = useStyles();

  return (
    <Tooltip title={title} placement="right">
      <ListItem
        button
        component="a"
        href={path}
        className={
          classNames(
            classes.item,
            { [classes['item--selected']]: path === location.pathname },
          )
        }
      >
        <FontAwesomeIcon icon={icon} />
      </ListItem>
    </Tooltip>
  );
};

SidebarMenuItemImpl.propTypes = propTypes;

export default withRouter(SidebarMenuItemImpl);
