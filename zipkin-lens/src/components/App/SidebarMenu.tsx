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
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  createStyles,
  makeStyles,
  ListItem,
  ListItemProps,
  Theme,
  Tooltip,
} from '@material-ui/core';
import classNames from 'classnames';
import React from 'react';
import { Link, useLocation } from 'react-router-dom';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
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
  }),
);

interface SidebarMenuProps extends React.DOMAttributes<HTMLElement> {
  title: string;
  path: string;
  icon: IconProp;
}

export const SidebarMenuImpl = React.forwardRef<
  HTMLDivElement,
  SidebarMenuProps
>(({ title, path, icon, ...others }, ref) => {
  const classes = useStyles();
  const location = useLocation();

  const listItemProps: ListItemProps<any> = {};
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
});

export default SidebarMenuImpl;
