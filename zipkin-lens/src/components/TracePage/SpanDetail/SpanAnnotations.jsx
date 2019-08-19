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
import React, { useState, useEffect, useCallback } from 'react';
import Box from '@material-ui/core/Box';
import Button from '@material-ui/core/Button';

import SpanAnnotation from './SpanAnnotation';
import SpanAnnotationGraph from './SpanAnnotationGraph';
import { detailedSpanPropTypes } from '../../../prop-types';

const propTypes = {
  span: detailedSpanPropTypes.isRequired,
};

const SpanAnnotations = ({ span }) => {
  const [annotationValue, setAnnotationValue] = useState();
  const [areAllAnnotationsOpened, setAreAllAnnotationsOpened] = useState(false);

  const handleAnnotationClick = useCallback((value) => {
    // If all annotations are already expanded, close them.
    if (areAllAnnotationsOpened) {
      setAreAllAnnotationsOpened(false);
    }
    // If the same annotation is selected, close it. (toggle)
    if (value === annotationValue) {
      setAnnotationValue(null);
    } else {
      setAnnotationValue(value);
    }
  }, [areAllAnnotationsOpened, annotationValue]);

  useEffect(() => {
    // Initialize states when the different span is selected.
    setAnnotationValue(null);
    setAreAllAnnotationsOpened(false);
  }, [span.spanId]);

  const selectedAnnotation = span.annotations.find(a => a.value === annotationValue);

  const handleToggleButtonClick = useCallback(() => {
    // When expanding all annotations, if any annotation is already selected, clear it.
    if (!areAllAnnotationsOpened) {
      setAnnotationValue(null);
    }
    setAreAllAnnotationsOpened(!areAllAnnotationsOpened);
  }, [areAllAnnotationsOpened]);

  return (
    <React.Fragment>
      <SpanAnnotationGraph
        serviceName={span.serviceName}
        annotations={span.annotations}
        onAnnotationClick={handleAnnotationClick}
        selectedAnnotationValue={annotationValue}
      />
      {
        /* eslint no-nested-ternary: 0 */
        areAllAnnotationsOpened ? (
          span.annotations.map(annotation => (
            <Box mt={1} key={annotation.value} data-testid="span-annotations--annotation">
              <SpanAnnotation annotation={annotation} />
            </Box>
          ))
        ) : (
          selectedAnnotation ? (
            <Box mt={1} data-testid="span-annotations--annotation">
              <SpanAnnotation annotation={selectedAnnotation} />
            </Box>
          ) : null
        )
      }
      <Box width="100%" display="flex" justifyContent="flex-end" mt={2}>
        <Button variant="contained" onClick={handleToggleButtonClick} data-testid="span-annotations--toggle-button">
          {areAllAnnotationsOpened ? 'hide annotations' : 'show all annotations'}
        </Button>
      </Box>
    </React.Fragment>
  );
};

SpanAnnotations.propTypes = propTypes;

export default SpanAnnotations;
