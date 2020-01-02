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
import React from 'react';

const useServiceBadge = (serviceName) => {
  const rectEl = React.useRef();
  const textEl = React.useRef();
  const [maxWidth, setMaxWidth] = React.useState();

  const resizeObserver = React.useRef();

  const handleIntersect = React.useCallback((entries) => {
    const [entry] = entries;
    if (entry.isIntersecting) {
      setMaxWidth(rectEl.current.getBBox().width);
      resizeObserver.current = new ResizeObserver(() => {
        setMaxWidth(rectEl.current.getBBox().width);
      });
      resizeObserver.current.observe(rectEl.current);
    } else if (resizeObserver.current) {
      resizeObserver.current.unobserve(rectEl.current);
    }
  }, []);

  React.useEffect(() => {
    const intersectionObserver = new IntersectionObserver(handleIntersect, {
      rootMargin: '200px',
    });

    // If rectEl.current is directly used in the cleanup function, eslint issues a warning.
    // To avoid this warning, assign rectEl.current into a local variable.
    const el = textEl.current;
    setTimeout(() => {
      intersectionObserver.observe(el);
    });
    return () => intersectionObserver.unobserve(el);
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  React.useEffect(() => {
    let textContent = serviceName;
    let textLength = textContent.length;
    textEl.current.textContent = textContent;

    while (textEl.current.getBBox().width > maxWidth && textLength >= 1) {
      textContent = textContent.slice(0, -1);
      textLength -= 1;
      textEl.current.textContent = `${textContent}...`;
    }
  }, [serviceName, maxWidth]);

  return [rectEl, textEl];
};

export default useServiceBadge;
