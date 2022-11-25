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

import { AdjustedSpan } from '../../../models/AdjustedTrace';

export const convertSpansToSpanTree = (spans: AdjustedSpan[]) => {
  const idToSpan = spans.reduce<{ [id: string]: AdjustedSpan }>((acc, cur) => {
    acc[cur.spanId] = cur;
    return acc;
  }, {});
  const unconsumedSpans = { ...idToSpan };

  const roots = spans.filter((span) => {
    return !span.parentId || !idToSpan[span.parentId];
  });
  roots.forEach((root) => {
    delete unconsumedSpans[root.spanId];
  });

  function fn(
    span: AdjustedSpan,
  ): AdjustedSpan & { children?: AdjustedSpan[] } {
    const children = spans.filter(
      (s) => !!unconsumedSpans[s.spanId] && s.parentId === span.spanId,
    );
    children.forEach((child) => {
      delete unconsumedSpans[child.spanId];
    });

    return {
      ...span,
      children: children ? children.map(fn) : undefined,
    };
  }

  return roots.map(fn);
};

export const convertSpansToServiceTree = (spans: AdjustedSpan[]) => {};
