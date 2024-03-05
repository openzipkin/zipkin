/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { Theme, Tooltip, withStyles } from '@material-ui/core';
import React, { useMemo } from 'react';
import { AdjustedAnnotation } from '../../../models/AdjustedTrace';
import { AnnotationTable } from '../AnnotationTable';

const HtmlTooltip = withStyles((theme: Theme) => ({
  tooltip: {
    backgroundColor: theme.palette.background.paper,
    color: theme.palette.text.primary,
    maxWidth: 500,
    fontSize: theme.typography.pxToRem(12),
    border: `1px solid ${theme.palette.divider}`,
    boxShadow: theme.shadows[3],
  },
}))(Tooltip);

type AnnotationTooltipProps = {
  children: JSX.Element;
  annotation: AdjustedAnnotation;
};

export const AnnotationTooltip = ({
  children,
  annotation,
}: AnnotationTooltipProps) => {
  const annotations = useMemo(() => [annotation], [annotation]);

  return (
    <HtmlTooltip
      title={<AnnotationTable annotations={annotations} />}
      placement="top"
    >
      {children}
    </HtmlTooltip>
  );
};
