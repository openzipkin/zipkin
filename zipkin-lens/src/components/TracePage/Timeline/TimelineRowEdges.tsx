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

import { Box, makeStyles } from '@material-ui/core';
import React, { memo, ReactNode, useMemo } from 'react';
import { TreeEdgeShapeType } from '../types';

const useStyles = makeStyles((theme) => ({
  horizontalAndVertical: {
    borderTop: `1px solid ${theme.palette.divider}`,
    borderLeft: `1px solid ${theme.palette.divider}`,
  },
  vertical: {
    borderLeft: `1px solid ${theme.palette.divider}`,
  },
  horizontal: {
    borderTop: `1px solid ${theme.palette.divider}`,
  },
}));

type TimelineRowEdgeProps = {
  treeEdgeShape: TreeEdgeShapeType[];
  rowHeight: number;
};

const TimelineRowEdgeImpl = ({
  treeEdgeShape,
  rowHeight,
}: TimelineRowEdgeProps) => {
  const classes = useStyles();

  const content = useMemo(() => {
    const commonProps = {
      height: rowHeight,
      width: `${100 / (treeEdgeShape.length + 1)}%`,
      style: {
        transform: `translateY(${(rowHeight / 4) * 3}px)`,
        zIndex: 100,
      },
    };

    let branch = true;
    const tree: ReactNode[] = [];

    tree.push(<Box {...commonProps} className={classes.horizontal} />);
    for (let i = treeEdgeShape.length - 1; i >= 0; i -= 1) {
      const shape = treeEdgeShape[i];
      const props = { ...commonProps, key: i };

      switch (shape) {
        case 'M':
          if (branch) {
            tree.push(
              <Box {...props} className={classes.horizontalAndVertical} />,
            );
          } else {
            tree.push(<Box {...props} className={classes.vertical} />);
          }
          branch = false;
          break;
        case 'E':
          branch = false;
          tree.push(<Box {...props} className={classes.horizontal} />);
          break;
        case 'B':
          tree.push(
            <Box {...props} className={classes.horizontalAndVertical} />,
          );
          break;
        default:
          if (branch) {
            tree.push(<Box {...props} className={classes.horizontal} />);
          } else {
            tree.push(<Box {...props} />);
          }
      }
    }
    tree.reverse();

    return tree;
  }, [classes, rowHeight, treeEdgeShape]);

  return (
    <Box pl={2} display="flex" width={120}>
      {content}
    </Box>
  );
};

export const TimelineRowEdge = memo(TimelineRowEdgeImpl);
