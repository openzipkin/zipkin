/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
// Create tests for convertSpansToSpanTree
// Define input data for a test that has two spans, parent and child, and parent span has
// a parent span ID that is not in the list of spans
// Define input data for a test that has two spans, parent and child, and parent span has
// a parent span ID that is in the list of spans

import { convertSpansToSpanTree } from './helpers';
import { describe, it, expect } from 'vitest';

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
