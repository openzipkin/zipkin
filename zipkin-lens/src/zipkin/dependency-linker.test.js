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
import { DependencyLinker } from './dependency-linker';
import { SpanNodeBuilder } from './span-node';

const debug = false; // switch to enable console output during tests
// in reverse order as reporting is more likely to occur this way
const trace = [
  {
    traceId: 'a',
    parentId: 'b',
    id: 'c',
    kind: 'CLIENT',
    localEndpoint: { serviceName: 'app' },
    remoteEndpoint: { serviceName: 'db' },
    tags: { error: true },
  },
  {
    traceId: 'a',
    parentId: 'a',
    id: 'b',
    kind: 'SERVER',
    localEndpoint: { serviceName: 'app' },
    remoteEndpoint: { serviceName: 'web' },
    shared: true,
  },
  {
    traceId: 'a',
    parentId: 'a',
    id: 'b',
    kind: 'CLIENT',
    localEndpoint: { serviceName: 'web' },
    remoteEndpoint: { serviceName: 'app' },
  },
  {
    traceId: 'a',
    id: 'a',
    kind: 'SERVER',
    localEndpoint: { serviceName: 'web' },
  },
];

// originally zipkin2.internal.DependencyLinkerTest.java
describe('DependencyLinker', () => {
  let dependencyLinker;

  beforeEach(() => {
    dependencyLinker = new DependencyLinker({ debug });
  });

  function putTrace(spans) {
    dependencyLinker.putTrace(new SpanNodeBuilder({ debug }).build(spans));
  }

  it('should return empty by default', () => {
    expect(dependencyLinker.link()).toEqual([]);
  });

  it('should link a trace', () => {
    putTrace(trace);

    expect(dependencyLinker.link()).toEqual([
      { parent: 'web', child: 'app', callCount: 1 },
      {
        parent: 'app',
        child: 'db',
        callCount: 1,
        errorCount: 1,
      },
    ]);
  });

  it('sum calls for each call to putTrace', () => {
    putTrace(trace);
    putTrace(trace);

    expect(dependencyLinker.link()).toEqual([
      { parent: 'web', child: 'app', callCount: 2 },
      {
        parent: 'app',
        child: 'db',
        callCount: 2,
        errorCount: 2,
      },
    ]);
  });

  function withLateParent() {
    const result = trace.slice(0);
    const missingParent = { ...trace[2] };
    delete missingParent.parentId;
    result[2] = missingParent;
    return result;
  }

  /*
   * This test shows that if a parent ID is stored late (ex because it wasn't propagated), the span
   * can resolve once it is.
   */
  it('should link spans when server is missing its parentId', () => {
    putTrace(withLateParent());

    expect(dependencyLinker.link()).toEqual([
      { parent: 'web', child: 'app', callCount: 1 },
      {
        parent: 'app',
        child: 'db',
        callCount: 1,
        errorCount: 1,
      },
    ]);
  });

  /*
   * This test shows that if a parent ID is stored late (ex because it wasn't propagated), the span
   * can resolve even if the client side is never sent.
   */
  it('should link spans when client it never sent', () => {
    putTrace(withLateParent().filter((s) => s !== trace[1])); // client span never sent

    expect(dependencyLinker.link()).toEqual([
      { parent: 'web', child: 'app', callCount: 1 },
      {
        parent: 'app',
        child: 'db',
        callCount: 1,
        errorCount: 1,
      },
    ]);
  });

  it('should link messaging spans by broker', () => {
    putTrace([
      {
        traceId: 'a',
        id: 'a',
        kind: 'PRODUCER',
        localEndpoint: { serviceName: 'producer' },
        remoteEndpoint: { serviceName: 'kafka' },
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
        kind: 'CONSUMER',
        localEndpoint: { serviceName: 'consumer' },
        remoteEndpoint: { serviceName: 'kafka' },
      },
    ]);

    expect(dependencyLinker.link()).toEqual([
      { parent: 'producer', child: 'kafka', callCount: 1 },
      { parent: 'kafka', child: 'consumer', callCount: 1 },
    ]);
  });

  it('should not conflate messaging when they have different brokers', () => {
    putTrace([
      {
        traceId: 'a',
        id: 'a',
        kind: 'PRODUCER',
        localEndpoint: { serviceName: 'producer' },
        remoteEndpoint: { serviceName: 'kafka1' },
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
        kind: 'CONSUMER',
        localEndpoint: { serviceName: 'consumer' },
        remoteEndpoint: { serviceName: 'kafka2' },
      },
    ]);

    expect(dependencyLinker.link()).toEqual([
      { parent: 'producer', child: 'kafka1', callCount: 1 },
      { parent: 'kafka2', child: 'consumer', callCount: 1 },
    ]);
  });

  it('should not link messaging spans missing broker', () => {
    putTrace([
      {
        traceId: 'a',
        id: 'a',
        kind: 'PRODUCER',
        localEndpoint: { serviceName: 'producer' },
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
        kind: 'CONSUMER',
        localEndpoint: { serviceName: 'consumer' },
      },
    ]);

    expect(dependencyLinker.link()).toEqual([]);
  });

  /* Shows we don't assume there's a direct link between producer and consumer. */
  it('should not link producer spans when missing broker', () => {
    putTrace([
      {
        traceId: 'a',
        id: 'a',
        kind: 'PRODUCER',
        localEndpoint: { serviceName: 'producer' },
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
        kind: 'CONSUMER',
        localEndpoint: { serviceName: 'consumer' },
        remoteEndpoint: { serviceName: 'kafka' },
      },
    ]);

    expect(dependencyLinker.link()).toEqual([
      { parent: 'kafka', child: 'consumer', callCount: 1 },
    ]);
  });

  it('should not link consumer spans when missing broker', () => {
    putTrace([
      {
        traceId: 'a',
        id: 'a',
        kind: 'PRODUCER',
        localEndpoint: { serviceName: 'producer' },
        remoteEndpoint: { serviceName: 'kafka' },
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
        kind: 'CONSUMER',
        localEndpoint: { serviceName: 'consumer' },
      },
    ]);

    expect(dependencyLinker.link()).toEqual([
      { parent: 'producer', child: 'kafka', callCount: 1 },
    ]);
  });

  /* When a server is the child of a producer span, make a link as it is really an RPC */
  it('should interpret producer -> server as RPC', () => {
    putTrace([
      {
        traceId: 'a',
        id: 'a',
        kind: 'PRODUCER',
        localEndpoint: { serviceName: 'producer' },
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
        kind: 'SERVER',
        localEndpoint: { serviceName: 'server' },
      },
    ]);

    expect(dependencyLinker.link()).toEqual([
      { parent: 'producer', child: 'server', callCount: 1 },
    ]);
  });

  /*
   * Servers most often join a span vs create a child. Make sure this works when a producer is used
   * instead of a client.
   */
  it('should interpret producer -> server as RPC, even sharing ID', () => {
    putTrace([
      {
        traceId: 'a',
        id: 'a',
        kind: 'PRODUCER',
        localEndpoint: { serviceName: 'producer' },
      },
      {
        traceId: 'a',
        id: 'a',
        kind: 'SERVER',
        localEndpoint: { serviceName: 'server' },
        shared: true,
      },
    ]);

    expect(dependencyLinker.link()).toEqual([
      { parent: 'producer', child: 'server', callCount: 1 },
    ]);
  });

  /*
   * Client might be used for historical reasons instead of PRODUCER. Don't link as the server-side
   * is authoritative.
   */
  it('should not interpret client as producer', () => {
    putTrace([
      {
        traceId: 'a',
        id: 'a',
        kind: 'CLIENT',
        localEndpoint: { serviceName: 'client' },
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
        kind: 'CONSUMER',
        localEndpoint: { serviceName: 'consumer' },
      },
    ]);

    expect(dependencyLinker.link()).toEqual([]);
  });

  /*
   * A root span can be a client-originated trace or a server receipt which knows its peer. In these
   * cases, the peer is known and kind establishes the direction.
   */
  it('should link spans directed by kind', () => {
    putTrace([
      {
        traceId: 'a',
        id: 'a',
        kind: 'SERVER',
        localEndpoint: { serviceName: 'server' },
        remoteEndpoint: { serviceName: 'client' },
      },
      {
        traceId: 'a',
        id: 'a',
        kind: 'CLIENT',
        localEndpoint: { serviceName: 'client' },
        remoteEndpoint: { serviceName: 'server' },
        shared: true,
      },
    ]);

    expect(dependencyLinker.link()).toEqual([
      { parent: 'client', child: 'server', callCount: 1 },
    ]);
  });

  it('link calls to uninstrumented servers', () => {
    putTrace([
      {
        traceId: 'a',
        id: 'a',
        kind: 'SERVER',
        localEndpoint: { serviceName: 'frontend' },
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
        kind: 'CLIENT',
        localEndpoint: { serviceName: 'frontend' },
        remoteEndpoint: { serviceName: 'backend' },
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'c',
        kind: 'CLIENT',
        localEndpoint: { serviceName: 'frontend' },
        remoteEndpoint: { serviceName: 'backend' },
      },
    ]);

    expect(dependencyLinker.link()).toEqual([
      { parent: 'frontend', child: 'backend', callCount: 2 },
    ]);
  });

  it('link calls to uninstrumented servers, including errors', () => {
    putTrace([
      {
        traceId: 'a',
        id: 'a',
        kind: 'SERVER',
        localEndpoint: { serviceName: 'frontend' },
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
        kind: 'CLIENT',
        localEndpoint: { serviceName: 'frontend' },
        remoteEndpoint: { serviceName: 'backend' },
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'c',
        kind: 'CLIENT',
        localEndpoint: { serviceName: 'frontend' },
        remoteEndpoint: { serviceName: 'backend' },
        tags: { error: '' },
      },
    ]);

    expect(dependencyLinker.link()).toEqual([
      {
        parent: 'frontend',
        child: 'backend',
        callCount: 2,
        errorCount: 1,
      },
    ]);
  });

  it('link incoming calls, using last RPC parent as service name', () => {
    putTrace([
      {
        traceId: 'a',
        id: 'a',
        kind: 'CLIENT',
        localEndpoint: { serviceName: 'frontend' },
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
        kind: 'SERVER',
        localEndpoint: { serviceName: 'backend' },
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'c',
        kind: 'SERVER',
        localEndpoint: { serviceName: 'backend' },
        tags: { error: '' },
      },
    ]);

    expect(dependencyLinker.link()).toEqual([
      {
        parent: 'frontend',
        child: 'backend',
        callCount: 2,
        errorCount: 1,
      },
    ]);
  });

  /*
   * Spans don't always include both the client and server service. When you know the kind, you can
   * link these without duplicating call count.
   */
  it('should link single host spans as one call', () => {
    putTrace([
      {
        traceId: 'a',
        id: 'a',
        kind: 'CLIENT',
        localEndpoint: { serviceName: 'client' },
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
        kind: 'SERVER',
        localEndpoint: { serviceName: 'server' },
      },
    ]);

    expect(dependencyLinker.link()).toEqual([
      { parent: 'client', child: 'server', callCount: 1 },
    ]);
  });

  it('should link single host spans as one error', () => {
    putTrace([
      {
        traceId: 'a',
        id: 'a',
        kind: 'CLIENT',
        localEndpoint: { serviceName: 'client' },
        tags: { error: '' },
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
        kind: 'SERVER',
        localEndpoint: { serviceName: 'server' },
        tags: { error: '' },
      },
    ]);

    expect(dependencyLinker.link()).toEqual([
      {
        parent: 'client',
        child: 'server',
        callCount: 1,
        errorCount: 1,
      },
    ]);
  });

  it('should link shared RPC span as one error', () => {
    putTrace([
      {
        traceId: 'a',
        id: 'a',
        kind: 'CLIENT',
        localEndpoint: { serviceName: 'client' },
        tags: { error: '' },
      },
      {
        traceId: 'a',
        id: 'b',
        kind: 'SERVER',
        localEndpoint: { serviceName: 'server' },
        tags: { error: '' },
        shared: true,
      },
    ]);

    expect(dependencyLinker.link()).toEqual([
      {
        parent: 'client',
        child: 'server',
        callCount: 1,
        errorCount: 1,
      },
    ]);
  });

  it('should prefer server name in RPC link', () => {
    putTrace([
      {
        traceId: 'a',
        id: 'a',
        kind: 'CLIENT',
        localEndpoint: { serviceName: 'client' },
        remoteEndpoint: { serviceName: 'elephant' },
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
        kind: 'SERVER',
        localEndpoint: { serviceName: 'server' },
      },
    ]);

    expect(dependencyLinker.link()).toEqual([
      {
        parent: 'client',
        child: 'server',
        callCount: 1,
      },
    ]);
  });

  /**
   * Spans are sometimes intermediated by an unknown type of span. Prefer the nearest server when
   * accounting for them.
   */
  it('tolerates missing localEndpoint between server and client', () => {
    putTrace([
      {
        traceId: 'a',
        id: 'a',
        kind: 'SERVER',
        localEndpoint: { serviceName: 'frontend' },
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
      },
      {
        traceId: 'a',
        parentId: 'b',
        id: 'c',
        kind: 'CLIENT',
        localEndpoint: { serviceName: 'frontend' },
        remoteEndpoint: { serviceName: 'backend' },
      },
      {
        traceId: 'a',
        parentId: 'b',
        id: 'd',
        kind: 'CLIENT',
        localEndpoint: { serviceName: 'frontend' },
        remoteEndpoint: { serviceName: 'backend' },
      },
    ]);

    expect(dependencyLinker.link()).toEqual([
      { parent: 'frontend', child: 'backend', callCount: 2 },
    ]);
  });

  it('should not link leaf nodes when remote service name is unknown', () => {
    putTrace([
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
        kind: 'SERVER',
        localEndpoint: { serviceName: 'frontend' },
      },
      {
        traceId: 'a',
        parentId: 'b',
        id: 'c',
        kind: 'CLIENT',
        localEndpoint: { serviceName: 'frontend' },
      },
      {
        traceId: 'a',
        parentId: 'b',
        id: 'd',
        kind: 'CLIENT',
        localEndpoint: { serviceName: 'frontend' },
      },
    ]);

    expect(dependencyLinker.link()).toEqual([]);
  });

  it('should create links when missing intermediate endpoint data', () => {
    putTrace([
      {
        traceId: 'a',
        id: 'a',
        kind: 'SERVER',
        localEndpoint: { serviceName: 'frontend' },
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b', // possibly missing client/server span
      },
      {
        traceId: 'a',
        parentId: 'b',
        id: 'c',
        kind: 'CLIENT',
        localEndpoint: { serviceName: 'backend' },
      },
      {
        traceId: 'a',
        parentId: 'b',
        id: 'd',
        kind: 'CLIENT',
        localEndpoint: { serviceName: 'backend' },
      },
    ]);

    expect(dependencyLinker.link()).toEqual([
      { parent: 'frontend', child: 'backend', callCount: 2 },
    ]);
  });

  it('should not attribute errors from uninstrumented links', () => {
    putTrace([
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
        kind: 'CLIENT',
        localEndpoint: { serviceName: 'frontend' },
      },
      // missing rpc span between here
      {
        traceId: 'a',
        parentId: 'b',
        id: 'c',
        kind: 'CLIENT',
        localEndpoint: { serviceName: 'backend' },
        tags: { error: '' },
      },
      {
        traceId: 'a',
        parentId: 'b',
        id: 'd',
        kind: 'CLIENT',
        localEndpoint: { serviceName: 'backend' },
        tags: { error: '' },
      },
    ]);

    expect(dependencyLinker.link()).toEqual([
      { parent: 'frontend', child: 'backend', callCount: 2 },
    ]);
  });

  /* Tag indicates a failed span, not an annotation */
  it('should not count annotation error as errorCount', () => {
    putTrace([
      {
        traceId: 'a',
        id: 'a',
        kind: 'CLIENT',
        localEndpoint: { serviceName: 'client' },
        annotations: [{ timestamp: 1, value: 'error' }],
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
        kind: 'SERVER',
        localEndpoint: { serviceName: 'server' },
        annotations: [{ timestamp: 1, value: 'error' }],
      },
    ]);

    expect(dependencyLinker.link()).toEqual([
      {
        parent: 'client',
        child: 'server',
        callCount: 1,
      },
    ]);
  });

  it('should link loopback', () => {
    putTrace([
      {
        traceId: 'a',
        id: 'a',
        kind: 'CLIENT',
        localEndpoint: { serviceName: 'frontend' },
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
        kind: 'SERVER',
        localEndpoint: { serviceName: 'frontend' },
      },
    ]);

    expect(dependencyLinker.link()).toEqual([
      { parent: 'frontend', child: 'frontend', callCount: 1 },
    ]);
  });

  it('should treat remote service names missing kind as RPC', () => {
    putTrace([
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
        localEndpoint: { serviceName: 'web' },
        remoteEndpoint: { serviceName: 'app' },
      },
      {
        traceId: 'a',
        parentId: 'b',
        id: 'c',
        localEndpoint: { serviceName: 'app' },
        remoteEndpoint: { serviceName: 'db' },
        tags: { error: true },
      },
    ]);

    expect(dependencyLinker.link()).toEqual([
      { parent: 'web', child: 'app', callCount: 1 },
      {
        parent: 'app',
        child: 'db',
        callCount: 1,
        errorCount: 1,
      },
    ]);
  });

  /* We cannot link if we don't know both service names. */
  it('should not link root RPC spans missing both service names', () => {
    [
      {
        traceId: 'a',
        id: 'a',
        kind: 'SERVER',
      },
      {
        traceId: 'a',
        id: 'a',
        kind: 'SERVER',
        localEndpoint: { serviceName: 'frontend' },
      },
      {
        traceId: 'a',
        id: 'a',
        kind: 'SERVER',
        remoteEndpoint: { serviceName: 'frontend' },
      },
      {
        traceId: 'a',
        id: 'a',
        kind: 'CLIENT',
      },
      {
        traceId: 'a',
        id: 'a',
        kind: 'CLIENT',
        localEndpoint: { serviceName: 'frontend' },
      },
      {
        traceId: 'a',
        id: 'a',
        kind: 'CLIENT',
        remoteEndpoint: { serviceName: 'frontend' },
      },
    ].forEach((root) => {
      putTrace([root]);
      expect(dependencyLinker.link()).toEqual([]);
    });
  });

  it('should not link peer spans on different hosts missing parentID', () => {
    const parentId = 'a'; // missing
    putTrace([
      {
        traceId: 'a',
        parentId,
        id: 'b',
        kind: 'SERVER',
        localEndpoint: { serviceName: 'server1' },
      },
      {
        traceId: 'a',
        parentId,
        id: 'c',
        kind: 'SERVER',
        localEndpoint: { serviceName: 'server2' },
      },
    ]);

    expect(dependencyLinker.link()).toEqual([]);
  });

  it('should tolerate missing root span', () => {
    const parentId = 'a'; // missing
    putTrace([
      {
        traceId: 'a',
        parentId,
        id: 'b',
        kind: 'CLIENT',
        localEndpoint: { serviceName: 'web' },
        remoteEndpoint: { serviceName: 'app' },
      },
      {
        traceId: 'a',
        parentId,
        id: 'b',
        kind: 'SERVER',
        localEndpoint: { serviceName: 'app' },
        remoteEndpoint: { serviceName: 'web' },
        shared: true,
      },
    ]);

    expect(dependencyLinker.link()).toEqual([
      { parent: 'web', child: 'app', callCount: 1 },
    ]);
  });
});
