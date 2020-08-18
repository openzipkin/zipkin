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
import { Theme, Typography, createStyles, makeStyles } from '@material-ui/core';
import classNames from 'classnames';
import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import styled from 'styled-components';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    root: {
      minHeight: 64,
      paddingRight: theme.spacing(2),
      paddingLeft: theme.spacing(2),
      color: theme.palette.grey[500],
      textDecoration: 'none',
      borderTop: `2px solid transparent`,
      borderBottom: `2px solid transparent`,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      cursor: 'pointer',
      '&:hover': {
        color: theme.palette.grey[100],
      },
    },
    'root--selected': {
      color: theme.palette.common.white,
      borderBottom: `2px solid ${theme.palette.common.white}`,
    },
  }),
);

interface HeaderMenuItemProps {
  icon: IconProp;
  title: string;
  path: string;
}

const HeaderMenuItem: React.FC<HeaderMenuItemProps> = ({
  icon,
  title,
  path,
}) => {
  const classes = useStyles();
  const location = useLocation();
  const isSelected = path === location.pathname;

  return (
    <Link
      to={path}
      className={classNames(classes.root, {
        [classes['root--selected']]: isSelected,
      })}
    >
      <FontAwesomeIcon icon={icon} size="lg" />
      <Title>{title}</Title>
    </Link>
  );
};

export default HeaderMenuItem;

const Title = styled(Typography).attrs({
  variant: 'body1',
})`
  margin-left: ${({ theme }) => theme.spacing(1.5)}px;
`;
