/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { describe, it, expect } from 'vitest';
import { SpanNode, SpanNodeBuilder } from './span-node';
import { clean } from './span-cleaner';

// originally zipkin2.internal.SpanNodeTest.java
describe('queueRootMostSpans', () => {
  it('should throw error on empty trace', () => {
    let error;
    try {
      new SpanNode().queueRootMostSpans();
    } catch (err) {
      error = err;
    }

    expect(error.message).toBe('Trace was empty');
  });

  it('should queue root', () => {
    const root = new SpanNode({ traceId: '1', id: '1' });
    expect(root.queueRootMostSpans()).toEqual([root]);
  });

  it('should queue root-most', () => {
    const root = new SpanNode();
    const a = new SpanNode({ traceId: '1', id: 'a' });
    root.addChild(a);
    const b = new SpanNode({ traceId: '1', id: 'b' });
    root.addChild(b);

    expect(root.queueRootMostSpans()).toEqual([a, b]);
  });
});

describe('SpanNode', () => {
  it('should construct without a span', () => {
    const span = { traceId: '1', id: '1' };
    const node = new SpanNode(span);

    expect(node.span).toEqual(span);
  });

  it('should have an undefined span field when there is no span', () => {
    const node = new SpanNode();

    expect(node.span).toBeUndefined();
  });

  it('should not allow setting an undefined span', () => {
    const node = new SpanNode();

    expect(() => node.setSpan()).toThrow('span was undefined');
  });

  const a = new SpanNode({ traceId: '1', id: 'a' });
  const b = new SpanNode({ traceId: '1', id: 'b' });
  const c = new SpanNode({ traceId: '1', id: 'c' });
  const d = new SpanNode({ traceId: '1', id: 'd' });
  // root(a) has children b, c, d
  a.addChild(b);
  a.addChild(c);
  a.addChild(d);
  const e = new SpanNode({ traceId: '1', id: 'e' });
  const f = new SpanNode({ traceId: '1', id: 'f' });
  const g = new SpanNode({ traceId: '1', id: '1' });
  // child(b) has children e, f, g
  b.addChild(e);
  b.addChild(f);
  b.addChild(g);
  const h = new SpanNode({ traceId: '1', id: '2' });
  // f has no children
  // child(g) has child h
  g.addChild(h);

  /*
   * The following tree should traverse in alphabetical order
   *
   *          a
   *        / | \
   *       b  c  d
   *      /|\
   *     e f g
   *          \
   *           h
   */
  it('should traverse breadth first', () => {
    const ids = [];
    a.traverse((s) => ids.push(s.id));

    expect(ids).toEqual(['a', 'b', 'c', 'd', 'e', 'f', '1', '2']);
  });
});

// originally zipkin2.internal.SpanNodeTest.java
describe('SpanNodeBuilder', () => {
  it('should throw error on empty trace', () => {
    let error;
    try {
      new SpanNodeBuilder({}).build([]);
    } catch (err) {
      error = err;
    }

    expect(error.message).toBe('Trace was empty');
  });

  // Makes sure that the trace tree is constructed based on parent-child, not by parameter order.
  it('should construct a trace tree', () => {
    const trace = [
      { traceId: 'a', id: 'a' },
      { traceId: 'a', parentId: 'a', id: 'b' },
      { traceId: 'a', parentId: 'b', id: 'c' },
      { traceId: 'a', parentId: 'c', id: 'd' },
    ].map(clean);

    // TRACE is sorted with root span first, lets reverse them to make
    // sure the trace is stitched together by id.
    const root = new SpanNodeBuilder({}).build(trace.slice(0).reverse());

    expect(root.span).toEqual(trace[0]);
    expect(root.children.map((n) => n.span)).toEqual([trace[1]]);

    const [child] = root.children;
    expect(child.children.map((n) => n.span)).toEqual([trace[2]]);
  });

  // input should be merged, but this ensures we are fine anyway
  it('should dedupe while constructing a trace tree', () => {
    const trace = [
      { traceId: 'a', id: 'a' },
      { traceId: 'a', id: 'a' },
      { traceId: 'a', id: 'a' },
    ];

    const root = new SpanNodeBuilder({}).build(trace);

    expect(root.span).toEqual(clean(trace[0]));
    expect(root.children.length).toBe(0);
  });

  it('should allocate spans missing parents to root', () => {
    const trace = [
      { traceId: 'a', id: 'b', timestamp: 1 },
      {
        traceId: 'a',
        parentId: 'b',
        id: 'c',
        timestamp: 2,
      },
      {
        traceId: 'a',
        parentId: 'b',
        id: 'd',
        timestamp: 3,
      },
      { traceId: 'a', id: 'e', timestamp: 4 },
      { traceId: 'a', id: 'f', timestamp: 5 },
    ].map(clean);

    const root = new SpanNodeBuilder({}).build(trace);

    const spans = [];
    root.traverse((span) => spans.push(span));
    expect(spans).toEqual(trace);
    expect(root.span.id).toBe('000000000000000b');
    expect(root.children.map((n) => n.span)).toEqual(trace.slice(1));
  });

  // spans are often reported depth-first, so it is possible to not have a root yet
  it('should construct a trace missing a root span', () => {
    const trace = [
      { traceId: 'a', parentId: 'a', id: 'b' },
      { traceId: 'a', parentId: 'a', id: 'c' },
      { traceId: 'a', parentId: 'a', id: 'd' },
    ].map(clean);

    const root = new SpanNodeBuilder({}).build(trace);

    expect(root.span).toBeUndefined();

    const spans = [];
    root.traverse((span) => spans.push(span));
    expect(spans).toEqual(trace);
  });

  // input should be well formed, but this ensures we are fine anyway
  it('should skip on cycle', () => {
    const trace = [{ traceId: 'a', parentId: 'b', id: 'b' }];

    const root = new SpanNodeBuilder({}).build(trace);

    expect(root.span).toEqual({
      traceId: '000000000000000a',
      id: '000000000000000b',
      annotations: [],
      tags: {},
    });
    expect(root.children.length).toBe(0);
  });

  it('should order children by timestamp', () => {
    const trace = [
      { traceId: 'a', id: '1' },
      {
        traceId: 'a',
        parentId: '1',
        id: 'a',
        timestamp: 2,
      },
      {
        traceId: 'a',
        parentId: '1',
        id: 'b',
        timestamp: 1,
      },
      { traceId: 'a', parentId: '1', id: 'c' },
    ].map(clean);

    const root = new SpanNodeBuilder({}).build(trace);

    expect(root.children.map((n) => n.span)).toEqual([
      trace[3],
      trace[2],
      trace[1],
    ]); // null first
  });

  it('should order children by timestamp when IPs change', () => {
    const trace = [
      {
        traceId: '1',
        parentId: 'a',
        id: 'c',
        kind: 'SERVER',
        shared: true,
        timestamp: 1,
        localEndpoint: { serviceName: 'my-service', ipv4: '10.2.3.4' },
      },
      {
        traceId: '1',
        parentId: 'c',
        id: 'b',
        kind: 'CLIENT',
        timestamp: 2,
        localEndpoint: { serviceName: 'my-service', ipv4: '169.2.3.4' },
      },
      {
        traceId: '1',
        parentId: 'c',
        id: 'a',
        timestamp: 3,
        localEndpoint: { serviceName: 'my-service', ipv4: '10.2.3.4' },
      },
    ].map(clean);

    const root = new SpanNodeBuilder({}).build(trace);

    const spans = [];
    root.traverse((span) => spans.push(span));
    expect(spans).toEqual(trace);
  });

  it('should remove incorrect shared flag on only root span', () => {
    const a = {
      traceId: '1',
      id: 'a',
      kind: 'SERVER',
      shared: true,
      timestamp: 1,
      localEndpoint: { serviceName: 'routing' },
    };
    // intentionally missing client per #3001
    const b = {
      traceId: '1',
      parentId: 'a',
      id: 'b',
      kind: 'SERVER',
      shared: true,
      timestamp: 2,
      localEndpoint: { serviceName: 'routing' },
    };
    // Also, intentionally missing client per #3001
    const c = {
      traceId: '1',
      parentId: 'a',
      id: 'c',
      kind: 'SERVER',
      shared: true,
      timestamp: 3,
      localEndpoint: { serviceName: 'yelp_main/biz' },
    };

    const trace = [a, b, c].map(clean);

    const root = new SpanNodeBuilder({}).build(trace);
    expect(root.span.id).toBe('000000000000000a');
    expect(root.span.shared).toBeUndefined();

    expect(root.children.map((n) => n.span)).toEqual([trace[1], trace[2]]);
  });
});
