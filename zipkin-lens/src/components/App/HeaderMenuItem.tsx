/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { Theme, Typography, createStyles, makeStyles } from '@material-ui/core';
import classNames from 'classnames';
import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import styled from 'styled-components';
import { getTheme } from '../../util/theme';

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
        color:
          getTheme() === 'dark'
            ? theme.palette.grey[200]
            : theme.palette.grey[100],
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
