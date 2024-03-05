/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import { describe, it, expect } from 'vitest';
import {
  ipsMatch,
  getClockSkew,
  treeCorrectedForClockSkew,
} from './clock-skew';
import { SpanNode, SpanNodeBuilder } from './span-node';
import { clean } from './span-cleaner';
import skewedTrace from '../test/data/skew';

export const frontend = {
  serviceName: 'frontend',
  ipv4: '172.17.0.13',
};

export const backend = {
  serviceName: 'backend',
  ipv4: '172.17.0.9',
};

describe('ipsMatch', () => {
  const ipv6 = { ipv6: '2001:db8::c001' };
  const ipv4 = { ipv4: '192.168.99.101' };
  const both = {
    ipv4: '192.168.99.101',
    ipv6: '2001:db8::c001',
  };

  it('IPs should not match when undefined', () => {
    expect(ipsMatch(undefined, undefined)).toEqual(false);
    expect(ipsMatch(undefined, ipv4)).toEqual(false);
    expect(ipsMatch(ipv4, undefined)).toEqual(false);
  });

  it('IPs should not match unless both sides have an IP', () => {
    const noIp = { serviceName: 'foo' };
    expect(ipsMatch(noIp, ipv4)).toEqual(false);
    expect(ipsMatch(noIp, ipv6)).toEqual(false);
    expect(ipsMatch(ipv4, noIp)).toEqual(false);
    expect(ipsMatch(ipv6, noIp)).toEqual(false);
  });

  it('IPs should not match when IPs are different', () => {
    const differentIpv6 = { ipv6: '2001:db8::c002' };
    const differentIpv4 = { ipv4: '192.168.99.102' };

    expect(ipsMatch(differentIpv4, ipv4)).toEqual(false);
    expect(ipsMatch(differentIpv6, ipv6)).toEqual(false);
    expect(ipsMatch(ipv4, differentIpv4)).toEqual(false);
    expect(ipsMatch(ipv6, differentIpv6)).toEqual(false);
  });

  it('IPs should match when ipv4 or ipv6 match', () => {
    expect(ipsMatch(ipv4, ipv4)).toEqual(true);
    expect(ipsMatch(both, ipv4)).toEqual(true);
    expect(ipsMatch(both, ipv6)).toEqual(true);
    expect(ipsMatch(ipv6, ipv6)).toEqual(true);
    expect(ipsMatch(ipv4, both)).toEqual(true);
    expect(ipsMatch(ipv6, both)).toEqual(true);
  });
});

describe('getClockSkew', () => {
  /*
   * Instrumentation bugs might result in spans that look like clock skew is at play. When skew
   * appears on the same host, we assume it is an instrumentation bug (rather than make it worse by
   * adjusting it!)
   */
  it('clock skew should only correct across different hosts', () => {
    const parent = new SpanNode(
      clean({
        traceId: '1',
        parentId: '2',
        id: '3',
        kind: 'CLIENT',
        localEndpoint: frontend,
        timestamp: 20,
        duration: 20,
      }),
    );
    const child = new SpanNode(
      clean({
        traceId: '1',
        parentId: '2',
        id: '3',
        kind: 'SERVER',
        localEndpoint: frontend,
        timestamp: 10, // skew
        duration: 10,
        shared: true,
      }),
    );
    parent.addChild(child);

    expect(getClockSkew(child)).toBeUndefined();
  });

  it('clock skew should only exist when client is behind server', () => {
    const parent = new SpanNode(
      clean({
        traceId: '1',
        parentId: '2',
        id: '3',
        kind: 'CLIENT',
        localEndpoint: frontend,
        timestamp: 20,
        duration: 20,
      }),
    );
    const child = new SpanNode(
      clean({
        traceId: '1',
        parentId: '2',
        id: '3',
        kind: 'SERVER',
        localEndpoint: backend,
        timestamp: 30, // no skew
        duration: 10,
        shared: true,
      }),
    );
    parent.addChild(child);

    expect(getClockSkew(child)).toBeUndefined();
  });

  it('clock skew should be attributed to the server endpoint', () => {
    const parent = new SpanNode(
      clean({
        traceId: '1',
        parentId: '2',
        id: '3',
        kind: 'CLIENT',
        localEndpoint: frontend,
        timestamp: 20,
        duration: 20,
      }),
    );
    const child = new SpanNode(
      clean({
        traceId: '1',
        parentId: '2',
        id: '3',
        kind: 'SERVER',
        localEndpoint: backend,
        timestamp: 10, // skew
        duration: 10,
        shared: true,
      }),
    );
    parent.addChild(child);

    // Skew correction pushes the server side forward, so the skew endpoint is the server
    expect(getClockSkew(child).endpoint).toEqual(backend);
  });

  it('clock skew should be attributed to the server endpoint even if missing shared flag', () => {
    const parent = new SpanNode(
      clean({
        traceId: '1',
        parentId: '2',
        id: '3',
        kind: 'CLIENT',
        localEndpoint: frontend,
        timestamp: 20,
        duration: 20,
      }),
    );
    const child = new SpanNode(
      clean({
        traceId: '1',
        parentId: '2',
        id: '3',
        kind: 'SERVER',
        localEndpoint: backend,
        timestamp: 10, // skew
        duration: 10,
      }),
    );
    parent.addChild(child);

    // Skew correction pushes the server side forward, so the skew endpoint is the server
    expect(getClockSkew(child).endpoint).toEqual(backend);
  });

  /*
   * Skew is relative to the server receive and centered by the difference between the server
   * duration and the client duration.
   */
  it('skew includes half the difference of client and server duration', () => {
    const client = clean({
      traceId: '1',
      parentId: '2',
      id: '3',
      kind: 'CLIENT',
      localEndpoint: frontend,
      timestamp: 20,
      duration: 20,
    });
    const server = clean({
      traceId: '1',
      parentId: '2',
      id: '3',
      kind: 'SERVER',
      localEndpoint: backend,
      timestamp: 10, // skew
      duration: 10,
      shared: true,
    });
    const parent = new SpanNode(client);
    const child = new SpanNode(server);
    parent.addChild(child);

    expect(getClockSkew(child).skew).toEqual(
      server.timestamp -
        client.timestamp - // how much the server is behind
        (client.duration - server.duration) / 2, // center the server by splitting what's left
    );
  });

  // Sets the server to 1us past the client
  it('skew on one-way spans assumes latency is at least 1us', () => {
    const client = clean({
      traceId: '1',
      parentId: '2',
      id: '3',
      kind: 'CLIENT',
      localEndpoint: frontend,
      timestamp: 20,
    });
    const server = clean({
      traceId: '1',
      parentId: '2',
      id: '3',
      kind: 'SERVER',
      localEndpoint: backend,
      timestamp: 10, // skew
      shared: true,
    });
    const parent = new SpanNode(client);
    const child = new SpanNode(server);
    parent.addChild(child);

    expect(getClockSkew(child).skew).toEqual(
      server.timestamp -
        client.timestamp - // how much server is behind
        1, // assume it takes at least 1us to get to the server
    );
  });

  // It is still impossible to reach the server before the client sends a request.
  it('skew on async server spans assumes latency is at least 1us', () => {
    const client = clean({
      traceId: '1',
      id: '2',
      kind: 'CLIENT',
      localEndpoint: frontend,
      timestamp: 20,
      duration: 5, // stops before server does
    });
    const server = clean({
      traceId: '1',
      id: '2',
      kind: 'SERVER',
      localEndpoint: backend,
      timestamp: 10, // skew
      duration: 10,
      shared: true,
    });
    const parent = new SpanNode(client);
    const child = new SpanNode(server);
    parent.addChild(child);

    expect(getClockSkew(child).skew).toEqual(
      server.timestamp -
        client.timestamp - // how much server is behind
        1, // assume it takes at least 1us to get to the server
    );
  });
});

// breadth first traversal, comparing timestamps between the parent and each child
function expectChildrenHappenAfterParent(root) {
  const queue = root.queueRootMostSpans();
  while (queue.length > 0) {
    const parent = queue.shift();

    parent.children.forEach((child) => {
      expect(child.span.timestamp).toBeGreaterThan(parent.span.timestamp);
      queue.push(child);
    });
  }
}

// breadth first traversal, comparing timestamps between children
function expectChildrenToBeInOrder(root) {
  const queue = root.queueRootMostSpans();
  while (queue.length > 0) {
    const current = queue.shift();

    const childCount = current.children.length;
    if (childCount === 0) continue;

    let lastChild = current.children[0].span;
    for (let i = 1; i < childCount; i += 1) {
      const currentChild = current.children[i].span;
      expect(currentChild.timestamp).toBeGreaterThan(lastChild.timestamp);
      lastChild = currentChild;
    }

    // process the next level of the tree
    current.children.forEach((child) => queue.push(child));
  }
}

// originally zipkin2.internal.CorrectForClockSkewTest.java
describe('treeCorrectedForClockSkew', () => {
  it('should correct skew', () => {
    const corrected = treeCorrectedForClockSkew(skewedTrace);
    expectChildrenHappenAfterParent(corrected);
    expectChildrenToBeInOrder(corrected);
  });

  it('should skip when headless', () => {
    const headless = [];
    skewedTrace.forEach((span) => {
      if (span.parentId) headless.push(span); // everything but root!
    });
    const notCorrected = treeCorrectedForClockSkew(headless);
    expect(notCorrected).toEqual(new SpanNodeBuilder({}).build(headless));
  });

  it('should skip on duplicate root', () => {
    const duplicate = [];
    skewedTrace.forEach((span) => duplicate.push(span));
    duplicate.push(
      clean({
        traceId: skewedTrace[0].traceId,
        id: 'cafebabe',
        name: 'curtain',
      }),
    );
    const notCorrected = treeCorrectedForClockSkew(duplicate);
    expect(notCorrected).toEqual(new SpanNodeBuilder({}).build(duplicate));
  });
});
