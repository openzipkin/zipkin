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
import { useLocation } from 'react-router';
import classNames from 'classnames';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { makeStyles } from '@material-ui/styles';
import ListItem from '@material-ui/core/ListItem';
import Tooltip from '@material-ui/core/Tooltip';

import { BASE_PATH } from '../../constants/api';

const propTypes = {
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

export const SidebarMenuImpl = React.forwardRef(({
  title,
  path,
  icon,
  ...others
}, ref) => {
  const classes = useStyles();
  const location = useLocation();

  return (
    <Tooltip title={title} placement="right">
      <ListItem
        button
        // TODO(anuraaga): Replace with react-router-dom Link for more smoothness after fixing state
        // management. We need to make sure to clear traces when returning to the discovery page.
        component="a"
        href={`${BASE_PATH}${path}`}
        ref={ref}
        className={
          classNames(
            classes.item,
            { [classes['item--selected']]: path === location.pathname },
          )
        }
        {...others}
      >
        <FontAwesomeIcon icon={icon} />
      </ListItem>
    </Tooltip>
  );
});

SidebarMenuImpl.propTypes = propTypes;

export default SidebarMenuImpl;
