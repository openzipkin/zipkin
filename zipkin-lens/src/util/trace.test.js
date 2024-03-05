/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { describe, it, expect } from 'vitest';
import { ensureV2TraceData } from './trace';
import v2Trace from '../../testdata/yelp.json';

describe('ensureV2', () => {
  it('does not throw on v2 format', () => {
    ensureV2TraceData(v2Trace);
  });

  it('should raise error if not a list', () => {
    let error;
    try {
      ensureV2TraceData();
    } catch (err) {
      error = err;
    }

    expect(error.message).toEqual('input is not a list');

    expect(() => {
      ensureV2TraceData({ traceId: 'a', id: 'b' });
    }).toThrow(error.message);
  });

  it('should raise error if missing trace ID or span ID', () => {
    let error;
    try {
      ensureV2TraceData([{ traceId: 'a' }]);
    } catch (err) {
      error = err;
    }

    expect(error.message).toEqual(
      'List<Span> implies at least traceId and id fields',
    );

    expect(() => {
      ensureV2TraceData([{ id: 'b' }]);
    }).toThrow(error.message);
  });

  it('should raise error if in v1 format', () => {
    let error;
    try {
      ensureV2TraceData([{ traceId: 'a', id: 'b', binaryAnnotations: [] }]);
    } catch (err) {
      error = err;
    }

    expect(error.message).toEqual(
      'v1 format is not supported. For help, contact https://gitter.im/openzipkin/zipkin',
    );
  });
});
