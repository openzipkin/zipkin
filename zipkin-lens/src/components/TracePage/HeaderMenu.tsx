/*
 * Copyright 2015-2022 The OpenZipkin Authors
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

import { Button, makeStyles, Menu, MenuItem } from '@material-ui/core';
import { Menu as MenuIcon } from '@material-ui/icons';
import React from 'react';
import AdjustedTrace from '../../models/AdjustedTrace';
import { useUiConfig } from '../UiConfig';

const useStyles = makeStyles(() => ({
  iconButton: {
    minWidth: 32,
    width: 32,
    height: 32,
  },
}));

type HeaderMenuProps = {
  trace: AdjustedTrace;
};

export const HeaderMenu = ({ trace }: HeaderMenuProps) => {
  const classes = useStyles();
  const config = useUiConfig();
  const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);

  const handleMenuButtonClick = (
    event: React.MouseEvent<HTMLButtonElement>,
  ) => {
    setAnchorEl(event.currentTarget);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
  };

  return (
    <>
      <Button
        variant="outlined"
        className={classes.iconButton}
        onClick={handleMenuButtonClick}
      >
        <MenuIcon fontSize="small" />
      </Button>
      <Menu
        anchorEl={anchorEl}
        keepMounted
        open={Boolean(anchorEl)}
        onClose={handleMenuClose}
      >
        <MenuItem onClick={handleMenuClose}>Download JSON</MenuItem>
        <MenuItem onClick={handleMenuClose}>View Logs</MenuItem>
        <MenuItem onClick={handleMenuClose}>Archive Trace</MenuItem>
      </Menu>
    </>
  );
};
