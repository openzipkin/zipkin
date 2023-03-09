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

// Create tests for convertSpansToSpanTree
// Define input data for a test that has two spans, parent and child, and parent span has
// a parent span ID that is not in the list of spans
// Define input data for a test that has two spans, parent and child, and parent span has
// a parent span ID that is in the list of spans

import { convertSpansToSpanTree } from './helpers';

describe('convertSpansToSpanTree', () => {
  it('should return an empty array when there are no spans', () => {
    const spans = [];
    const result = convertSpansToSpanTree(spans);
    expect(result).toEqual([]);
  });

  it('should return an array with correct root span', () => {
    const spans = [
      {
        spanId: 'a',
        spanName: 'root',
        parentId: null,
        childIds: ['b'],
      },
      {
        spanId: 'b',

        spanName: 'child',
        parentId: 'a',
        childIds: [],
      },
    ];

    const result = convertSpansToSpanTree(spans);
    expect(result.length).toEqual(1);
    expect(result[0].spanId).toEqual('a');
  });

  // This test is consistent with 'should work when missing root span' in
  // span-cleaner.test.js
  it('should return an array with the root element which is missing parent', () => {
    const spans = [
      {
        spanId: 'a',
        spanName: 'missing parent',
        parentId: 'm',
        childIds: ['b'],
      },
      {
        spanId: 'b',
        spanName: 'child',
        parentId: 'a',
        childIds: [],
      },
    ];

    const result = convertSpansToSpanTree(spans);
    expect(result.length).toEqual(1);
    expect(result[0].spanId).toEqual('a');
  });

  it('should return an array with two root elements which are missing parent', () => {
    const spans = [
      {
        spanId: 'a',
        spanName: 'missing parent',
        parentId: 'm',
        childIds: [],
      },
      {
        spanId: 'b',
        spanName: 'missing parent',
        parentId: 'm',
        childIds: [],
      },
    ];

    const result = convertSpansToSpanTree(spans);
    expect(result.length).toEqual(2);
    expect(result[0].spanId).toEqual('a');
    expect(result[1].spanId).toEqual('b');
  });
});
