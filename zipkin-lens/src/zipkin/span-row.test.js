/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { describe, it, expect } from 'vitest';
import { newSpanRow, getErrorType, formatEndpoint } from './span-row';
import { clean } from './span-cleaner';

// bad traces from https://github.com/openzipkin/zipkin/issues/2829
import malformedTrace from '../test/data/malformed'; // Many data problems from Kong
import envoyTrace from '../../testdata/envoy.json'; // Slight problem: missing the local service name

// endpoints from zipkin2.TestObjects
const frontend = {
  serviceName: 'frontend',
  ipv4: '127.0.0.1',
  port: 8080,
};

const backend = {
  serviceName: 'backend',
  ipv4: '192.168.99.101',
  port: 9000,
};

describe('getErrorType', () => {
  it('should return none if annotations and tags are empty', () => {
    const span = {
      traceId: '1e223ff1f80f1c69',
      id: 'bf396325699c84bf',
      annotations: [],
      tags: {},
    };
    expect(getErrorType(span, 'none')).toBe('none');
  });

  it('should return none if ann=noError and tag=noError', () => {
    const span = {
      traceId: '1e223ff1f80f1c69',
      id: 'bf396325699c84bf',
      annotations: [{ timestamp: 1, value: 'not' }],
      tags: { not: 'error' },
    };
    expect(getErrorType(span, 'none')).toBe('none');
  });

  it('should return none if second span has ann=noError and tag=noError', () => {
    const span = {
      traceId: '1e223ff1f80f1c69',
      parentId: '1e223ff1f80f1c69',
      id: 'bf396325699c84bf',
      annotations: [{ timestamp: 1, value: 'not' }],
      tags: { not: 'error' },
    };
    expect(getErrorType(span, 'none')).toBe('none');
  });

  it('should return critical if ann empty and tag=error', () => {
    const span = {
      traceId: '1e223ff1f80f1c69',
      id: 'bf396325699c84bf',
      annotations: [],
      tags: { error: '' },
    };
    expect(getErrorType(span, 'none')).toBe('critical');
  });

  it('should return critical if ann=noError and tag=error', () => {
    const span = {
      traceId: '1e223ff1f80f1c69',
      id: 'bf396325699c84bf',
      annotations: [{ timestamp: 1, value: 'not' }],
      tags: { error: '' },
    };
    expect(getErrorType(span, 'none')).toBe('critical');
  });

  it('should return critical if ann=error and tag=error', () => {
    const span = {
      traceId: '1e223ff1f80f1c69',
      id: 'bf396325699c84bf',
      annotations: [{ timestamp: 1, value: 'error' }],
      tags: { error: '' },
    };
    expect(getErrorType(span, 'none')).toBe('critical');
  });

  it('should return critical if span1 has ann=error and span2 has tag=error', () => {
    const span = {
      traceId: '1e223ff1f80f1c69',
      parentId: '1e223ff1f80f1c69',
      id: 'bf396325699c84bf',
      annotations: [],
      tags: { error: '' },
    };
    expect(getErrorType(span, 'transient')).toBe('critical');
  });

  it('should return transient if ann=error and tag noError', () => {
    const span = {
      traceId: '1e223ff1f80f1c69',
      id: 'bf396325699c84bf',
      annotations: [{ timestamp: 1, value: 'error' }],
      tags: { not: 'error' },
    };
    expect(getErrorType(span)).toBe('transient');
  });
});

describe('SPAN v2 -> spanRow Conversion', () => {
  // originally zipkin2.v1.SpanConverterTest.client
  it('converts client span', () => {
    const v2 = {
      traceId: '1',
      parentId: '2',
      id: '3',
      name: 'get',
      kind: 'CLIENT',
      timestamp: 1472470996199000,
      duration: 207000,
      localEndpoint: frontend,
      remoteEndpoint: backend,
      annotations: [
        { value: 'ws', timestamp: 1472470996238000 },
        { value: 'wr', timestamp: 1472470996403000 },
      ],
      tags: {
        'http.path': '/api',
        'clnt/finagle.version': '6.45.0',
      },
    };

    const spanRow = {
      parentId: '2',
      spanId: '3',
      spanName: 'get',
      timestamp: 1472470996199000,
      duration: 207000,
      annotations: [
        {
          isDerived: true,
          value: 'Client Start',
          timestamp: 1472470996199000,
          endpoint: '127.0.0.1:8080 (frontend)',
        },
        {
          isDerived: false,
          value: 'Wire Send',
          timestamp: 1472470996238000,
          endpoint: '127.0.0.1:8080 (frontend)',
        },
        {
          isDerived: false,
          value: 'Wire Receive',
          timestamp: 1472470996403000,
          endpoint: '127.0.0.1:8080 (frontend)',
        },
        {
          isDerived: true,
          value: 'Client Finish',
          timestamp: 1472470996406000,
          endpoint: '127.0.0.1:8080 (frontend)',
        },
      ],
      tags: [
        {
          key: 'http.path',
          value: '/api',
          endpoints: ['127.0.0.1:8080 (frontend)'],
        },
        {
          key: 'clnt/finagle.version',
          value: '6.45.0',
          endpoints: ['127.0.0.1:8080 (frontend)'],
        },
        {
          key: 'Server Address',
          value: '192.168.99.101:9000 (backend)',
        },
      ],
      serviceName: 'frontend', // prefer the local address vs remote
      serviceNames: ['backend', 'frontend'],
      errorType: 'none',
    };

    expect(newSpanRow([v2], false)).toEqual(spanRow);
  });

  it('should not duplicate service names', () => {
    const converted = newSpanRow(
      [
        clean({
          traceId: '1',
          id: '3',
          localEndpoint: frontend,
          remoteEndpoint: frontend,
        }),
      ],
      false,
    );

    expect(converted.serviceNames).toEqual(['frontend']);
  });

  // originally zipkin2.v1.SpanConverterTest.SpanConverterTest.client_unfinished
  it('converts incomplete client span', () => {
    const v2 = clean({
      traceId: '1',
      parentId: '2',
      id: '3',
      name: 'get',
      kind: 'CLIENT',
      timestamp: 1472470996199000,
      localEndpoint: frontend,
      annotations: [{ value: 'ws', timestamp: 1472470996238000 }],
    });

    const spanRow = {
      parentId: '0000000000000002',
      spanId: '0000000000000003',
      spanName: 'get',
      timestamp: 1472470996199000,
      duration: 0,
      annotations: [
        {
          isDerived: true,
          value: 'Client Start',
          timestamp: 1472470996199000,
          endpoint: '127.0.0.1:8080 (frontend)',
        },
        {
          isDerived: false,
          value: 'Wire Send',
          timestamp: 1472470996238000,
          endpoint: '127.0.0.1:8080 (frontend)',
        },
      ],
      tags: [], // prefers empty array to nil
      serviceName: 'frontend',
      serviceNames: ['frontend'],
      errorType: 'none',
    };

    expect(newSpanRow([v2], false)).toEqual(spanRow);
  });

  // originally zipkin2.v1.SpanConverterTest.client_kindInferredFromAnnotation
  it('infers cr annotation', () => {
    const v2 = clean({
      traceId: '1',
      parentId: '2',
      id: '3',
      name: 'get',
      timestamp: 1472470996199000,
      duration: 207000,
      localEndpoint: frontend,
      annotations: [{ value: 'cs', timestamp: 1472470996199000 }],
    });

    const spanRow = {
      parentId: '0000000000000002',
      spanId: '0000000000000003',
      spanName: 'get',
      timestamp: 1472470996199000,
      duration: 207000,
      annotations: [
        {
          isDerived: true,
          value: 'Client Start',
          timestamp: 1472470996199000,
          endpoint: '127.0.0.1:8080 (frontend)',
        },
        {
          isDerived: true,
          value: 'Client Finish',
          timestamp: 1472470996406000,
          endpoint: '127.0.0.1:8080 (frontend)',
        },
      ],
      tags: [],
      serviceName: 'frontend',
      serviceNames: ['frontend'],
      errorType: 'none',
    };

    expect(newSpanRow([v2], false)).toEqual(spanRow);
  });

  // originally zipkin2.v1.SpanConverterTest.lateRemoteEndpoint_cr
  it('converts client span reporting remote endpoint with late cr', () => {
    const v2 = clean({
      traceId: '1',
      parentId: '2',
      id: '3',
      name: 'get',
      kind: 'CLIENT',
      localEndpoint: frontend,
      remoteEndpoint: backend,
      annotations: [{ value: 'cr', timestamp: 1472470996199000 }],
    });

    const spanRow = {
      parentId: '0000000000000002',
      spanId: '0000000000000003',
      spanName: 'get',
      duration: 0,
      annotations: [
        {
          isDerived: true,
          value: 'Client Finish',
          timestamp: 1472470996199000,
          endpoint: '127.0.0.1:8080 (frontend)',
        },
      ],
      tags: [{ key: 'Server Address', value: '192.168.99.101:9000 (backend)' }],
      serviceName: 'frontend',
      serviceNames: ['backend', 'frontend'],
      errorType: 'none',
    };

    expect(newSpanRow([v2], false)).toEqual(spanRow);
  });

  // originally zipkin2.v1.SpanConverterTest.lateRemoteEndpoint_sa
  it('converts late remoteEndpoint to sa', () => {
    const v2 = clean({
      traceId: '1',
      parentId: '2',
      id: '3',
      remoteEndpoint: backend,
    });

    const converted = newSpanRow([v2], false);
    expect(converted.tags).toEqual([
      { key: 'Server Address', value: '192.168.99.101:9000 (backend)' },
    ]);
    expect(converted.serviceName).toEqual('unknown');
    expect(converted.serviceNames).toEqual(['backend']);
  });

  // originally zipkin2.v1.SpanConverterTest.noAnnotationsExceptAddresses
  it('converts when remoteEndpoint exist without kind', () => {
    const v2 = clean({
      traceId: '1',
      parentId: '2',
      id: '3',
      name: 'get',
      timestamp: 1472470996199000,
      duration: 207000,
      localEndpoint: frontend,
      remoteEndpoint: backend,
    });

    const spanRow = {
      parentId: '0000000000000002',
      spanId: '0000000000000003',
      spanName: 'get',
      timestamp: 1472470996199000,
      duration: 207000,
      annotations: [],
      tags: [
        { key: 'Local Address', value: '127.0.0.1:8080 (frontend)' },
        { key: 'Server Address', value: '192.168.99.101:9000 (backend)' },
      ],
      serviceName: 'frontend',
      serviceNames: ['backend', 'frontend'],
      errorType: 'none',
    };

    expect(newSpanRow([v2], false)).toEqual(spanRow);
  });

  // originally zipkin2.v1.SpanConverterTest.server
  it('converts root server span', () => {
    // let's pretend there was no caller, so we don't set shared flag
    const v2 = clean({
      traceId: '1',
      id: '2',
      name: 'get',
      kind: 'SERVER',
      localEndpoint: backend,
      remoteEndpoint: frontend,
      timestamp: 1472470996199000,
      duration: 207000,
      tags: {
        'http.path': '/api',
        'finagle.version': '6.45.0',
      },
    });

    const spanRow = {
      spanId: '0000000000000002',
      spanName: 'get',
      timestamp: 1472470996199000,
      duration: 207000,
      annotations: [
        {
          isDerived: true,
          value: 'Server Start',
          timestamp: 1472470996199000,
          endpoint: '192.168.99.101:9000 (backend)',
        },
        {
          isDerived: true,
          value: 'Server Finish',
          timestamp: 1472470996406000,
          endpoint: '192.168.99.101:9000 (backend)',
        },
      ],
      tags: [
        {
          key: 'http.path',
          value: '/api',
          endpoints: ['192.168.99.101:9000 (backend)'],
        },
        {
          key: 'finagle.version',
          value: '6.45.0',
          endpoints: ['192.168.99.101:9000 (backend)'],
        },
        { key: 'Client Address', value: '127.0.0.1:8080 (frontend)' },
      ],
      serviceName: 'backend',
      serviceNames: ['backend', 'frontend'],
      errorType: 'none',
    };

    expect(newSpanRow([v2], false)).toEqual(spanRow);
  });

  // originally zipkin2.v1.SpanConverterTest.missingEndpoints
  it('converts span with no endpoints', () => {
    const v2 = clean({
      traceId: '1',
      parentId: '1',
      id: '2',
      name: 'foo',
      timestamp: 1472470996199000,
      duration: 207000,
    });

    const spanRow = {
      parentId: '0000000000000001',
      spanId: '0000000000000002',
      spanName: 'foo',
      serviceName: 'unknown',
      timestamp: 1472470996199000,
      duration: 207000,
      annotations: [],
      tags: [],
      serviceNames: [],
      errorType: 'none',
    };

    const expected = newSpanRow([v2], false);
    expect(spanRow).toEqual(expected);
  });

  // originally zipkin2.v1.SpanConverterTest.coreAnnotation
  it('converts v2 span retaining a cs annotation', () => {
    const v2 = clean({
      traceId: '1',
      parentId: '1',
      id: '2',
      name: 'foo',
      timestamp: 1472470996199000,
      annotations: [{ value: 'cs', timestamp: 1472470996199000 }],
    });

    const spanRow = {
      parentId: '0000000000000001',
      spanId: '0000000000000002',
      spanName: 'foo',
      timestamp: 1472470996199000,
      duration: 0,
      annotations: [
        {
          isDerived: true,
          value: 'Client Start',
          timestamp: 1472470996199000,
          endpoint: 'unknown',
        },
      ],
      tags: [],
      serviceName: 'unknown',
      serviceNames: [],
      errorType: 'none',
    };

    const expected = newSpanRow([v2], false);
    expect(spanRow).toEqual(expected);
  });

  // originally zipkin2.v1.SpanConverterTest.server_shared_spanRow_no_timestamp_duration
  it('when shared server span is missing its client, write its timestamp and duration', () => {
    const v2 = clean({
      traceId: '1',
      parentId: '2',
      id: '3',
      name: 'get',
      kind: 'SERVER',
      shared: true,
      localEndpoint: backend,
      timestamp: 1472470996199000,
      duration: 207000,
    });

    const spanRow = {
      parentId: '0000000000000002',
      spanId: '0000000000000003',
      spanName: 'get',
      timestamp: 1472470996199000,
      duration: 207000,
      annotations: [
        {
          isDerived: true,
          value: 'Server Start',
          timestamp: 1472470996199000,
          endpoint: '192.168.99.101:9000 (backend)',
        },
        {
          isDerived: true,
          value: 'Server Finish',
          timestamp: 1472470996406000,
          endpoint: '192.168.99.101:9000 (backend)',
        },
      ],
      tags: [],
      serviceName: 'backend',
      serviceNames: ['backend'],
      errorType: 'none',
    };

    const expected = newSpanRow([v2], false);
    expect(spanRow).toEqual(expected);
  });

  // originally zipkin2.v1.SpanConverterTest.server_incomplete_shared
  it('converts incomplete shared server span', () => {
    const v2 = clean({
      traceId: '1',
      parentId: '2',
      id: '3',
      name: 'get',
      kind: 'SERVER',
      shared: true,
      localEndpoint: backend,
      timestamp: 1472470996199000,
    });

    const spanRow = {
      parentId: '0000000000000002',
      spanId: '0000000000000003',
      spanName: 'get',
      timestamp: 1472470996199000, // When we only have a shared timestamp, we should use it
      duration: 0,
      annotations: [
        {
          isDerived: true,
          value: 'Server Start',
          timestamp: 1472470996199000,
          endpoint: '192.168.99.101:9000 (backend)',
        },
      ],
      tags: [],
      serviceName: 'backend',
      serviceNames: ['backend'],
      errorType: 'none',
    };

    const expected = newSpanRow([v2], false);
    expect(spanRow).toEqual(expected);
  });

  // originally zipkin2.v1.SpanConverterTest.lateRemoteEndpoint_ss
  it('converts late incomplete server span with remote endpoint', () => {
    const v2 = clean({
      traceId: '1',
      id: '2',
      name: 'get',
      kind: 'SERVER',
      localEndpoint: backend,
      remoteEndpoint: frontend,
      annotations: [{ value: 'ss', timestamp: 1472470996199000 }],
    });

    const spanRow = {
      spanId: '0000000000000002',
      spanName: 'get',
      duration: 0,
      annotations: [
        {
          isDerived: true,
          value: 'Server Finish',
          timestamp: 1472470996199000,
          endpoint: '192.168.99.101:9000 (backend)',
        },
      ],
      tags: [{ key: 'Client Address', value: '127.0.0.1:8080 (frontend)' }],
      serviceName: 'backend',
      serviceNames: ['backend', 'frontend'],
      errorType: 'none',
    };

    expect(newSpanRow([v2], false)).toEqual(spanRow);
  });

  // originally zipkin2.v1.SpanConverterTest.lateRemoteEndpoint_ca
  it('converts late remote endpoint server span', () => {
    const v2 = clean({
      traceId: '1',
      id: '2',
      kind: 'SERVER',
      remoteEndpoint: frontend,
    });

    const converted = newSpanRow([v2], false);
    expect(converted.tags).toEqual([
      { key: 'Client Address', value: '127.0.0.1:8080 (frontend)' },
    ]);
    expect(converted.serviceName).toEqual('unknown');
    expect(converted.serviceNames).toEqual(['frontend']);
  });

  // originally zipkin2.v1.SpanConverterTest.localSpan_emptyComponent
  it('converts local span', () => {
    const v2 = clean({
      traceId: '1',
      id: '2',
      name: 'local',
      localEndpoint: { serviceName: 'frontend' },
      timestamp: 1472470996199000,
      duration: 207000,
    });

    const spanRow = {
      spanId: '0000000000000002',
      spanName: 'local',
      timestamp: 1472470996199000,
      duration: 207000,
      annotations: [],
      tags: [{ key: 'Local Address', value: 'frontend' }],
      serviceName: 'frontend',
      serviceNames: ['frontend'],
      errorType: 'none',
    };

    expect(newSpanRow([v2], false)).toEqual(spanRow);
  });

  // originally zipkin2.v1.SpanConverterTest.producer_remote
  it('converts incomplete producer span', () => {
    const v2 = clean({
      traceId: '1',
      parentId: '2',
      id: '3',
      name: 'send',
      kind: 'PRODUCER',
      timestamp: 1472470996199000,
      localEndpoint: frontend,
    });

    const spanRow = {
      parentId: '0000000000000002',
      spanId: '0000000000000003',
      spanName: 'send',
      timestamp: 1472470996199000,
      duration: 0,
      annotations: [
        {
          isDerived: true,
          value: 'Producer Start',
          timestamp: 1472470996199000,
          endpoint: '127.0.0.1:8080 (frontend)',
        },
      ],
      tags: [],
      serviceName: 'frontend',
      serviceNames: ['frontend'],
      errorType: 'none',
    };

    expect(newSpanRow([v2], false)).toEqual(spanRow);
  });

  // originally zipkin2.v1.SpanConverterTest.producer_duration
  it('converts producer span', () => {
    const v2 = clean({
      traceId: '1',
      parentId: '2',
      id: '3',
      name: 'send',
      kind: 'PRODUCER',
      localEndpoint: frontend,
      timestamp: 1472470996199000,
      duration: 51000,
    });

    const spanRow = {
      parentId: '0000000000000002',
      spanId: '0000000000000003',
      spanName: 'send',
      timestamp: 1472470996199000,
      duration: 51000,
      annotations: [
        {
          isDerived: true,
          value: 'Producer Start',
          timestamp: 1472470996199000,
          endpoint: '127.0.0.1:8080 (frontend)',
        },
        {
          isDerived: true,
          value: 'Producer Finish',
          timestamp: 1472470996250000,
          endpoint: '127.0.0.1:8080 (frontend)',
        },
      ],
      tags: [],
      serviceName: 'frontend',
      serviceNames: ['frontend'],
      errorType: 'none',
    };

    expect(newSpanRow([v2], false)).toEqual(spanRow);
  });

  // originally zipkin2.v1.SpanConverterTest.consumer
  it('converts incomplete consumer span', () => {
    const v2 = clean({
      traceId: '1',
      parentId: '2',
      id: '3',
      name: 'next-message',
      kind: 'CONSUMER',
      timestamp: 1472470996199000,
      localEndpoint: backend,
    });

    const spanRow = {
      parentId: '0000000000000002',
      spanId: '0000000000000003',
      spanName: 'next-message',
      timestamp: 1472470996199000,
      duration: 0,
      annotations: [
        {
          isDerived: true,
          value: 'Consumer Start',
          timestamp: 1472470996199000,
          endpoint: '192.168.99.101:9000 (backend)',
        },
      ],
      tags: [],
      serviceName: 'backend',
      serviceNames: ['backend'],
      errorType: 'none',
    };

    expect(newSpanRow([v2], false)).toEqual(spanRow);
  });

  // originally zipkin2.v1.SpanConverterTest.consumer_remote
  it('converts incomplete consumer span with remote endpoint', () => {
    const v2 = clean({
      traceId: '1',
      parentId: '2',
      id: '3',
      name: 'next-message',
      kind: 'CONSUMER',
      timestamp: 1472470996199000,
      localEndpoint: backend,
      remoteEndpoint: { serviceName: 'kafka' },
    });

    const spanRow = {
      parentId: '0000000000000002',
      spanId: '0000000000000003',
      spanName: 'next-message',
      timestamp: 1472470996199000,
      duration: 0,
      annotations: [
        {
          isDerived: true,
          value: 'Consumer Start',
          timestamp: 1472470996199000,
          endpoint: '192.168.99.101:9000 (backend)',
        },
      ],
      tags: [{ key: 'Broker Address', value: 'kafka' }],
      serviceName: 'backend',
      serviceNames: ['backend', 'kafka'],
      errorType: 'none',
    };

    expect(newSpanRow([v2], false)).toEqual(spanRow);
  });

  // originally zipkin2.v1.SpanConverterTest.consumer_duration
  it('converts consumer span', () => {
    const v2 = clean({
      traceId: '1',
      parentId: '2',
      id: '3',
      name: 'send',
      kind: 'CONSUMER',
      localEndpoint: backend,
      timestamp: 1472470996199000,
      duration: 51000,
    });

    const spanRow = {
      parentId: '0000000000000002',
      spanId: '0000000000000003',
      spanName: 'send',
      timestamp: 1472470996199000,
      duration: 51000,
      annotations: [
        {
          isDerived: true,
          value: 'Consumer Start',
          timestamp: 1472470996199000,
          endpoint: '192.168.99.101:9000 (backend)',
        },
        {
          isDerived: true,
          value: 'Consumer Finish',
          timestamp: 1472470996250000,
          endpoint: '192.168.99.101:9000 (backend)',
        },
      ],
      tags: [],
      serviceName: 'backend',
      serviceNames: ['backend'],
      errorType: 'none',
    };

    expect(newSpanRow([v2], false)).toEqual(spanRow);
  });

  it('should prefer ipv6', () => {
    const localEndpoint = {
      serviceName: 'there',
      ipv4: '10.57.50.84',
      ipv6: '2001:db8::c001',
      port: 80,
    };

    const v2 = clean({
      traceId: '1',
      id: '2',
      localEndpoint,
    });

    const spanRow = newSpanRow([v2], false);
    expect(spanRow.tags.map((s) => s.value)).toEqual([
      '[2001:db8::c001]:80 (there)',
    ]);
  });

  it('should not require endpoint serviceName', () => {
    const v2 = clean({
      traceId: '1',
      id: '2',
      kind: 'CLIENT',
      timestamp: 1,
      localEndpoint: {
        ipv6: '2001:db8::c001',
      },
    });

    const spanRow = newSpanRow([v2], false);
    expect(spanRow.annotations.map((s) => s.endpoint)).toEqual([
      '[2001:db8::c001]',
    ]);
  });

  it('converts client leaf spans using its remote service name', () => {
    const v2 = {
      traceId: '1',
      parentId: '2',
      id: '3',
      name: 'get',
      kind: 'CLIENT',
      timestamp: 1472470996199000,
      duration: 207000,
      localEndpoint: frontend,
      remoteEndpoint: backend,
      annotations: [
        { value: 'ws', timestamp: 1472470996238000 },
        { value: 'wr', timestamp: 1472470996403000 },
      ],
      tags: {
        'http.path': '/api',
        'clnt/finagle.version': '6.45.0',
      },
    };

    const spanRow = {
      parentId: '2',
      spanId: '3',
      spanName: 'get',
      timestamp: 1472470996199000,
      duration: 207000,
      annotations: [
        {
          isDerived: true,
          value: 'Client Start',
          timestamp: 1472470996199000,
          endpoint: '127.0.0.1:8080 (frontend)',
        },
        {
          isDerived: false,
          value: 'Wire Send',
          timestamp: 1472470996238000,
          endpoint: '127.0.0.1:8080 (frontend)',
        },
        {
          isDerived: false,
          value: 'Wire Receive',
          timestamp: 1472470996403000,
          endpoint: '127.0.0.1:8080 (frontend)',
        },
        {
          isDerived: true,
          value: 'Client Finish',
          timestamp: 1472470996406000,
          endpoint: '127.0.0.1:8080 (frontend)',
        },
      ],
      tags: [
        {
          key: 'http.path',
          value: '/api',
          endpoints: ['127.0.0.1:8080 (frontend)'],
        },
        {
          key: 'clnt/finagle.version',
          value: '6.45.0',
          endpoints: ['127.0.0.1:8080 (frontend)'],
        },
        {
          key: 'Server Address',
          value: '192.168.99.101:9000 (backend)',
        },
      ],
      serviceName: 'backend', // prefer client remote address on leaf client-only spans
      serviceNames: ['backend', 'frontend'],
      errorType: 'none',
    };

    expect(newSpanRow([v2], true)).toEqual(spanRow);
  });
});

describe('newSpanRow', () => {
  const clientSpan = clean({
    traceId: '1',
    parentId: '2',
    id: '3',
    name: 'get',
    kind: 'CLIENT',
    timestamp: 1472470996199000,
    duration: 207000,
    localEndpoint: frontend,
  });
  const serverSpan = clean({
    traceId: '1',
    parentId: '2',
    id: '3',
    name: 'get',
    kind: 'SERVER',
    timestamp: 1472470996238000,
    duration: 165000,
    localEndpoint: backend,
    shared: true,
  });
  const expectedSpanRow = {
    parentId: '0000000000000002',
    spanId: '0000000000000003',
    spanName: 'get',
    timestamp: 1472470996199000,
    duration: 207000,
    annotations: [
      {
        isDerived: true,
        value: 'Client Start',
        timestamp: 1472470996199000,
        endpoint: '127.0.0.1:8080 (frontend)',
      },
      {
        isDerived: true,
        value: 'Server Start',
        timestamp: 1472470996238000,
        endpoint: '192.168.99.101:9000 (backend)',
      },
      {
        isDerived: true,
        value: 'Server Finish',
        timestamp: 1472470996403000,
        endpoint: '192.168.99.101:9000 (backend)',
      },
      {
        isDerived: true,
        value: 'Client Finish',
        timestamp: 1472470996406000,
        endpoint: '127.0.0.1:8080 (frontend)',
      },
    ],
    tags: [],
    serviceName: 'backend', // prefer server in shared span
    serviceNames: ['backend', 'frontend'],
    errorType: 'none',
  };

  it('should merge server and client span', () => {
    const spanRow = newSpanRow([serverSpan, clientSpan], false);

    expect(spanRow).toEqual(expectedSpanRow);
  });

  it('should merge client and server span', () => {
    const spanRow = newSpanRow([clientSpan, serverSpan], false);

    expect(spanRow).toEqual(expectedSpanRow);
  });

  // originally zipkin2.v1.SpanConverterTest.mergeWhenTagsSentSeparately
  it('should add late server addr', () => {
    const spanRow = newSpanRow(
      [
        clientSpan,
        clean({
          traceId: '1',
          id: '3',
          remoteEndpoint: backend,
        }),
      ],
      false,
    );

    expect(spanRow.tags).toEqual([
      { key: 'Server Address', value: '192.168.99.101:9000 (backend)' },
    ]);
  });

  // originally zipkin2.v1.SpanConverterTest.mergePrefersServerSpanName
  it('should overwrite client name with server name', () => {
    const spanRow = newSpanRow(
      [
        clientSpan,
        clean({
          traceId: '1',
          id: '3',
          name: 'get /users/:userId',
          timestamp: 1472470996238000,
          kind: 'SERVER',
          localEndpoint: backend,
          shared: true,
        }),
      ],
      false,
    );

    expect(spanRow.spanName).toBe('get /users/:userId');
  });

  // originally zipkin2.v1.SpanConverterTest.timestampAndDurationMergeWithClockSkew
  it('should merge timestamp and duration even with skew', () => {
    const leftTimestamp = 100 * 1000;
    const leftDuration = 35 * 1000;

    const rightTimestamp = 200 * 1000;
    const rightDuration = 30 * 1000;

    const leftSpan = clean({
      traceId: '1',
      parentId: '2',
      id: '3',
      name: 'get',
      kind: 'CLIENT',
      timestamp: leftTimestamp,
      duration: leftDuration,
      localEndpoint: frontend,
    });

    const rightSpan = clean({
      traceId: '1',
      id: '3',
      name: 'get',
      kind: 'SERVER',
      timestamp: rightTimestamp,
      duration: rightDuration,
      localEndpoint: backend,
      shared: true,
    });

    const leftFirst = newSpanRow([leftSpan, rightSpan], false);
    const rightFirst = newSpanRow([rightSpan, leftSpan], false);

    [leftFirst, rightFirst].forEach((completeSpan) => {
      expect(completeSpan.timestamp).toEqual(leftTimestamp);
      expect(completeSpan.duration).toEqual(leftDuration);

      // ensure if server isn't propagated the parent ID, it is still ok.
      expect(completeSpan.parentId).toBe('0000000000000002');
    });
  });

  it('should not overwrite client name with empty', () => {
    const spanRow = newSpanRow(
      [
        clientSpan,
        clean({
          traceId: '1',
          id: '3',
          timestamp: 1472470996238000,
          kind: 'SERVER',
          localEndpoint: backend,
          shared: true,
        }),
      ],
      false,
    );

    expect(spanRow.spanName).toBe(clientSpan.name);
  });

  it('should dedupe annotations with same timestamp and value', () => {
    const spanRow = newSpanRow(
      [
        clean({
          traceId: '1',
          parentId: '2',
          id: '3',
          kind: 'CLIENT',
          localEndpoint: frontend,
          annotations: [{ timestamp: 1, value: 'hit' }],
        }),
        clean({
          traceId: '1',
          parentId: '2',
          id: '3',
          kind: 'CLIENT',
          localEndpoint: frontend,
          annotations: [{ timestamp: 1, value: 'hit' }],
        }),
      ],
      false,
    );

    expect(spanRow.annotations).toEqual([
      {
        timestamp: 1,
        value: 'hit',
        endpoint: '127.0.0.1:8080 (frontend)',
        isDerived: false,
      },
    ]);
  });

  it('should merge endpoints on shared tag', () => {
    const spanRow = newSpanRow(
      [
        clean({
          traceId: '1',
          parentId: '2',
          id: '3',
          kind: 'CLIENT',
          localEndpoint: frontend,
          tags: { 'http.path': '/foo' },
        }),
        clean({
          traceId: '1',
          parentId: '2',
          id: '3',
          shared: true,
          kind: 'SERVER',
          localEndpoint: backend,
          tags: { 'http.path': '/foo' },
        }),
      ],
      false,
    );

    expect(spanRow.tags).toEqual([
      {
        key: 'http.path',
        value: '/foo',
        endpoints: [
          '127.0.0.1:8080 (frontend)',
          '192.168.99.101:9000 (backend)',
        ],
      },
    ]);
  });

  it('should show difference in tag values per endpoint', () => {
    const spanRow = newSpanRow(
      [
        clean({
          traceId: '1',
          parentId: '2',
          id: '3',
          kind: 'CLIENT',
          localEndpoint: frontend,
          tags: { 'http.path': '/foo' },
        }),
        clean({
          traceId: '1',
          parentId: '2',
          id: '3',
          shared: true,
          kind: 'SERVER',
          localEndpoint: backend,
          tags: { 'http.path': '/foo/redirected' },
        }),
      ],
      false,
    );

    expect(spanRow.tags).toEqual([
      {
        key: 'http.path',
        value: '/foo',
        endpoints: ['127.0.0.1:8080 (frontend)'],
      },
      {
        key: 'http.path',
        value: '/foo/redirected',
        endpoints: ['192.168.99.101:9000 (backend)'],
      },
    ]);
  });

  // This prevents white screens due to failed required property tests downstream
  it('should backfill data in malformed trace', () => {
    malformedTrace.concat(envoyTrace).forEach((span) => {
      const spanRow = newSpanRow([clean(span)], false);
      expect(spanRow.duration).toBeDefined();
      expect(spanRow.serviceName).toBeDefined();
      expect(spanRow.spanName).toBeDefined();
      spanRow.annotations.forEach((a) => {
        expect(a.endpoint).toBeDefined();
      });
    });
  });
});

describe('formatEndpoint', () => {
  it('should format ip and port', () => {
    expect(formatEndpoint({ ipv4: '150.151.152.153', port: 5000 })).toBe(
      '150.151.152.153:5000',
    );
  });

  it('should not use port when missing or zero', () => {
    expect(formatEndpoint({ ipv4: '150.151.152.153' })).toBe('150.151.152.153');
    expect(formatEndpoint({ ipv4: '150.151.152.153', port: 0 })).toBe(
      '150.151.152.153',
    );
  });

  it('should put service name in parenthesis', () => {
    expect(
      formatEndpoint({
        ipv4: '150.151.152.153',
        port: 9042,
        serviceName: 'cassandra',
      }),
    ).toBe('150.151.152.153:9042 (cassandra)');
    expect(
      formatEndpoint({
        ipv4: '150.151.152.153',
        serviceName: 'cassandra',
      }),
    ).toBe('150.151.152.153 (cassandra)');
  });

  it('should not show empty service name', () => {
    expect(
      formatEndpoint({
        ipv4: '150.151.152.153',
        port: 9042,
        serviceName: '',
      }),
    ).toBe('150.151.152.153:9042');
    expect(
      formatEndpoint({
        ipv4: '150.151.152.153',
        serviceName: '',
      }),
    ).toBe('150.151.152.153');
  });

  it('should show service name missing IP', () => {
    expect(
      formatEndpoint({
        serviceName: 'rabbit',
      }),
    ).toBe('rabbit');
  });

  it('should not crash on no data', () => {
    expect(formatEndpoint({})).toBe('');
  });

  it('should put ipv6 in brackets', () => {
    expect(
      formatEndpoint({
        ipv6: '2001:db8::c001',
        port: 9042,
        serviceName: 'cassandra',
      }),
    ).toBe('[2001:db8::c001]:9042 (cassandra)');

    expect(
      formatEndpoint({
        ipv6: '2001:db8::c001',
        port: 9042,
      }),
    ).toBe('[2001:db8::c001]:9042');

    expect(
      formatEndpoint({
        ipv6: '2001:db8::c001',
      }),
    ).toBe('[2001:db8::c001]');
  });
});
