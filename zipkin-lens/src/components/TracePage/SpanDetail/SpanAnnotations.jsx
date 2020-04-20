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
import { Trans } from '@lingui/macro';
import React, { useState, useEffect, useCallback, useMemo } from 'react';
import Box from '@material-ui/core/Box';
import Button from '@material-ui/core/Button';

import { generateAnnotationKey } from './util';
import SpanAnnotation from './SpanAnnotation';
import SpanAnnotationGraph from './SpanAnnotationGraph';
import { detailedSpanPropTypes } from '../../../prop-types';

const propTypes = {
  span: detailedSpanPropTypes.isRequired,
};

const SpanAnnotations = React.memo(({ span }) => {
  const [currentAnnotationKey, setCurrentAnnotationKey] = useState();
  const [areAllAnnotationsOpened, setAreAllAnnotationsOpened] = useState(false);

  const handleAnnotationCircleClick = useCallback(
    (annotation) => {
      // If all annotations have already been expanded, close all of them.
      if (areAllAnnotationsOpened) {
        setAreAllAnnotationsOpened(false);
      }
      // If an annotation that has already been focused is selected, unfocus it.
      const key = generateAnnotationKey(annotation);
      setCurrentAnnotationKey((prev) => (prev === key ? null : key));
    },
    [areAllAnnotationsOpened],
  );

  // Initialize states when a different span is selected.
  useEffect(() => {
    setCurrentAnnotationKey(null);
    setAreAllAnnotationsOpened(false);
  }, [span.spanId]);

  const currentAnnotation = useMemo(
    () =>
      span.annotations.find((annotation) => {
        const key = generateAnnotationKey(annotation);
        return key === currentAnnotationKey;
      }),
    [currentAnnotationKey, span.annotations],
  );

  const handleExpandToggle = useCallback(() => {
    // When expanding all annotations, if any annotation is already selected, clear it.
    if (!areAllAnnotationsOpened) {
      setCurrentAnnotationKey(null);
    }
    setAreAllAnnotationsOpened((prev) => !prev);
  }, [areAllAnnotationsOpened]);

  return (
    <>
      <SpanAnnotationGraph
        serviceName={span.serviceName}
        annotations={span.annotations}
        onAnnotationCircleClick={handleAnnotationCircleClick}
        currentAnnotaionKey={currentAnnotationKey}
      />
      {
        /* eslint no-nested-ternary: 0 */
        areAllAnnotationsOpened ? (
          span.annotations.map((annotation) => (
            <Box
              mt={1}
              key={generateAnnotationKey(annotation)}
              data-testid="span-annotations--annotation"
            >
              <SpanAnnotation annotation={annotation} />
            </Box>
          ))
        ) : currentAnnotation ? (
          <Box mt={1} data-testid="span-annotations--annotation">
            <SpanAnnotation annotation={currentAnnotation} />
          </Box>
        ) : null
      }
      <Box width="100%" display="flex" justifyContent="flex-end" mt={2}>
        <Button
          variant="contained"
          onClick={handleExpandToggle}
          data-testid="span-annotations--toggle-button"
        >
          {areAllAnnotationsOpened ? (
            <Trans>hide annotations</Trans>
          ) : (
            <Trans>show all annotations</Trans>
          )}
        </Button>
      </Box>
    </>
  );
});

SpanAnnotations.propTypes = propTypes;

export default SpanAnnotations;
