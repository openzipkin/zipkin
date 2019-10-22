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
import React, { useCallback } from 'react';
import { withRouter } from 'react-router';
import classNames from 'classnames';
import { withStyles } from '@material-ui/styles';
import Box from '@material-ui/core/Box';
import ListItem from '@material-ui/core/ListItem';
import Tooltip from '@material-ui/core/Tooltip';

const propTypes = {
  history: PropTypes.shape({ push: PropTypes.func.isRequired }).isRequired,
  location: PropTypes.shape({ pathname: PropTypes.string.isRequired }).isRequired,
  isExternal: PropTypes.bool,
  title: PropTypes.string.isRequired,
  links: PropTypes.arrayOf(PropTypes.string).isRequired,
  logo: PropTypes.string.isRequired,
  classes: PropTypes.shape({}).isRequired,
};

const defaultProps = {
  isExternal: false,
};

const style = theme => ({
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
});

export const SidebarMenuItemImpl = ({
  history,
  location,
  isExternal,
  title,
  links,
  logo,
  classes,
}) => {
  const handleClick = useCallback(() => {
    history.push(links[0]);
  }, [history, links]);

  if (isExternal) {
    return (
      <Tooltip
        title={title}
        placement="right"
        data-testid="tooltip"
      >
        <ListItem
          button
          component="a"
          href={links[0]}
          className={classes.item}
          data-testid="list-item"
        >
          <Box
            component="span"
            className={logo}
            data-testid="logo"
          />
        </ListItem>
      </Tooltip>
    );
  }

  return (
    <Tooltip
      title={title}
      placement="right"
      data-testid="tooltip"
    >
      <ListItem
        button
        onClick={handleClick}
        className={
          classNames(
            classes.item,
            { [classes['item--selected']]: links.includes(location.pathname) },
          )
        }
        data-testid="list-item"
      >
        <Box
          component="span"
          className={logo}
          data-testid="logo"
        />
      </ListItem>
    </Tooltip>
  );
};

SidebarMenuItemImpl.propTypes = propTypes;
SidebarMenuItemImpl.defaultProps = defaultProps;

export default withRouter(
  withStyles(style)(SidebarMenuItemImpl),
);
