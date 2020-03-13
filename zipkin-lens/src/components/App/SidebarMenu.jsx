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
import { Link, useLocation } from 'react-router-dom';
import classNames from 'classnames';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { makeStyles } from '@material-ui/styles';
import ListItem from '@material-ui/core/ListItem';
import Tooltip from '@material-ui/core/Tooltip';

const propTypes = {
  title: PropTypes.string.isRequired,
  path: PropTypes.string.isRequired,
  icon: PropTypes.shape({}).isRequired,
};

const useStyles = makeStyles((theme) => ({
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

export const SidebarMenuImpl = React.forwardRef(
  ({ title, path, icon, ...others }, ref) => {
    const classes = useStyles();
    const location = useLocation();

    const listItemProps = {};
    if (path.startsWith('https://') || path.startsWith('http://')) {
      listItemProps.component = 'a';
      listItemProps.href = path;
      listItemProps.target = '_blank';
      listItemProps.rel = 'no-opener';
    } else {
      listItemProps.component = Link;
      listItemProps.to = path;
    }

    return (
      <Tooltip title={title} placement="right">
        <ListItem
          {...listItemProps}
          button
          ref={ref}
          className={classNames(classes.item, {
            [classes['item--selected']]: path === location.pathname,
          })}
          {...others}
        >
          <FontAwesomeIcon icon={icon} />
        </ListItem>
      </Tooltip>
    );
  },
);

SidebarMenuImpl.propTypes = propTypes;

export default SidebarMenuImpl;
