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
