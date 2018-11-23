const {merge, mergeV2ById} = require('../js/spanCleaner');

// endpoints from zipkin2.TestObjects
const frontend = {
  serviceName: 'frontend',
  ipv4: '127.0.0.1',
  port: 8080
};

const backend = {
  serviceName: 'backend',
  ipv4: '192.168.99.101',
  port: 9000
};
const clientSpan = {
  traceId: '0000000000000001',
  id: '0000000000000002',
  name: 'get',
  kind: 'CLIENT',
  timestamp: 1472470996199000,
  duration: 207000,
  localEndpoint: frontend,
  remoteEndpoint: backend,
  annotations: [
    {timestamp: 1472470996238000, value: 'ws'},
    {timestamp: 1472470996403000, value: 'wr'}
  ],
  tags: {
    'http.path': '/api',
    'clnt/finagle.version': '6.45.0',
  }
};
const serverSpan = {
  traceId: '0000000000000001',
  id: '0000000000000002',
  kind: 'SERVER',
  timestamp: 1472470996308713,
  duration: 10319,
  localEndpoint: backend,
  remoteEndpoint: frontend,
  annotations: [],
  tags: {},
  shared: true
};
const oneOfEach = { // has every field set
  traceId: '7180c278b62e8f6a216a2aea45d08fc9',
  parentId: '0000000000000001',
  id: '0000000000000002',
  kind: 'SERVER',
  name: 'get',
  timestamp: 1,
  duration: 3,
  localEndpoint: backend,
  remoteEndpoint: frontend,
  annotations: [{timestamp: 2, value: 'foo'}],
  tags: {'http.path': '/api'},
  shared: true,
  debug: true
};

describe('merge', () => {
  it('should work on redundant data', () => {
    const merged = merge(oneOfEach, oneOfEach);
    expect(merged).to.deep.equal(oneOfEach);
  });

  it('should merge flags', () => {
    const merged = merge(
      {
        traceId: '0000000000000001',
        id: '0000000000000002',
        annotations: [],
        tags: {},
        shared: true
      },
      {
        traceId: '0000000000000001',
        id: '0000000000000002',
        annotations: [],
        tags: {},
        debug: true
      }
    );

    expect(merged).to.deep.equal({
      traceId: '0000000000000001',
      id: '0000000000000002',
      annotations: [],
      tags: {},
      debug: true,
      shared: true
    });
  });

  it('should merge annotations', () => {
    const merged = merge(
      {
        traceId: '0000000000000001',
        id: '0000000000000002',
        annotations: [{timestamp: 1, value: 'a'}, {timestamp: 1, value: 'b'}],
        tags: {}
      },
      {
        traceId: '0000000000000001',
        id: '0000000000000002',
        annotations: [{timestamp: 1, value: 'b'}, {timestamp: 1, value: 'c'}],
        tags: {}
      }
    );

    expect(merged).to.deep.equal({
      traceId: '0000000000000001',
      id: '0000000000000002',
      annotations: [
        {timestamp: 1, value: 'a'},
        {timestamp: 1, value: 'b'},
        {timestamp: 1, value: 'c'}
      ],
      tags: {}
    });
  });

  it('should merge tags', () => {
    const merged = merge(
      {
        traceId: '0000000000000001',
        id: '0000000000000002',
        annotations: [],
        tags: {1: 'a', 2: 'a'}
      },
      {
        traceId: '0000000000000001',
        id: '0000000000000002',
        annotations: [],
        tags: {2: 'a', 3: 'a'}
      }
    );

    expect(merged).to.deep.equal({
      traceId: '0000000000000001',
      id: '0000000000000002',
      annotations: [],
      tags: {1: 'a', 2: 'a', 3: 'a'}
    });
  });

  it('should merge local endpoint', () => {
    const merged = merge(
      {
        traceId: '0000000000000001',
        id: '0000000000000002',
        annotations: [],
        tags: {}
      },
      {
        traceId: '0000000000000001',
        id: '0000000000000002',
        localEndpoint: frontend,
        annotations: [],
        tags: {}
      }
    );

    expect(merged).to.deep.equal({
      traceId: '0000000000000001',
      id: '0000000000000002',
      localEndpoint: frontend,
      annotations: [],
      tags: {}
    });
  });

  it('should merge local endpoint - partial', () => {
    const merged = merge(
      {
        traceId: '0000000000000001',
        id: '0000000000000002',
        localEndpoint: {serviceName: 'a'},
        annotations: [],
        tags: {}
      },
      {
        traceId: '0000000000000001',
        id: '0000000000000002',
        localEndpoint: {ipv4: '192.168.99.101'},
        annotations: [],
        tags: {}
      }
    );

    expect(merged).to.deep.equal({
      traceId: '0000000000000001',
      id: '0000000000000002',
      localEndpoint: {serviceName: 'a', ipv4: '192.168.99.101'},
      annotations: [],
      tags: {}
    });
  });

  it('should merge remote endpoint', () => {
    const merged = merge(
      {
        traceId: '0000000000000001',
        id: '0000000000000002',
        annotations: [],
        tags: {}
      },
      {
        traceId: '0000000000000001',
        id: '0000000000000002',
        remoteEndpoint: frontend,
        annotations: [],
        tags: {}
      }
    );

    expect(merged).to.deep.equal({
      traceId: '0000000000000001',
      id: '0000000000000002',
      remoteEndpoint: frontend,
      annotations: [],
      tags: {}
    });
  });

  it('should merge remote endpoint - partial', () => {
    const merged = merge(
      {
        traceId: '0000000000000001',
        id: '0000000000000002',
        remoteEndpoint: {serviceName: 'a'},
        annotations: [],
        tags: {}
      },
      {
        traceId: '0000000000000001',
        id: '0000000000000002',
        remoteEndpoint: {ipv4: '192.168.99.101'},
        annotations: [],
        tags: {}
      }
    );

    expect(merged).to.deep.equal({
      traceId: '0000000000000001',
      id: '0000000000000002',
      remoteEndpoint: {serviceName: 'a', ipv4: '192.168.99.101'},
      annotations: [],
      tags: {}
    });
  });
});

describe('mergeV2ById', () => {
  it('should cleanup spans', () => {
    const spans = mergeV2ById([
      {
        traceId: '22222222222222222', // longer than 64-bit
        parentId: 'a',
        id: '3',
        name: '', // empty name should be scrubbed
        duration: 0 // zero duration should be scrubbed
      },
      {
        traceId: '22222222222222222',
        parentId: 'a',
        remoteEndpoint: {}, // empty
        id: 'a', // self-referencing
        kind: 'SERVER',
        timestamp: 1,
        duration: 10,
        localEndpoint: frontend
      },
      {
        traceId: '22222222222222222',
        parentId: 'a',
        id: 'b',
        timestamp: 2,
        kind: 'CLIENT',
        name: 'unknown', // unknown name should be scrubbed
        localEndpoint: frontend
      }
    ]);

    expect(spans).to.deep.equal([
      {
        traceId: '00000000000000022222222222222222',
        id: '000000000000000a',
        kind: 'SERVER',
        timestamp: 1,
        duration: 10,
        localEndpoint: {
          serviceName: 'frontend',
          ipv4: '127.0.0.1',
          port: 8080
        },
        annotations: [],
        tags: {}
      },
      {
        traceId: '00000000000000022222222222222222',
        parentId: '000000000000000a',
        id: '0000000000000003',
        annotations: [],
        tags: {}
      },
      {
        traceId: '00000000000000022222222222222222',
        parentId: '000000000000000a',
        id: '000000000000000b',
        kind: 'CLIENT',
        timestamp: 2,
        localEndpoint: {
          serviceName: 'frontend',
          ipv4: '127.0.0.1',
          port: 8080
        },
        annotations: [],
        tags: {}
      }
    ]);
  });

  /*
   * Some don't propagate the server's parent ID which creates a race condition. Try to unwind it.
   *
   * See https://github.com/openzipkin/zipkin/pull/1745
   */
  it('should backfill missing parent id on shared span', () => {
    const spans = mergeV2ById([
      {
        traceId: 'a',
        id: 'a',
        kind: 'SERVER',
        localEndpoint: {serviceName: 'frontend'}
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
        kind: 'CLIENT',
        localEndpoint: {serviceName: 'frontend'}
      },
      {
        traceId: 'a',
        id: 'b',
        kind: 'SERVER',
        localEndpoint: {serviceName: 'backend'},
        shared: true
      }
    ]);

    expect(spans).to.deep.equal([
      {
        traceId: '000000000000000a',
        id: '000000000000000a',
        kind: 'SERVER',
        localEndpoint: {serviceName: 'frontend'},
        annotations: [],
        tags: {}
      },
      {
        traceId: '000000000000000a',
        parentId: '000000000000000a',
        id: '000000000000000b',
        kind: 'CLIENT',
        localEndpoint: {serviceName: 'frontend'},
        annotations: [],
        tags: {}
      },
      {
        traceId: '000000000000000a',
        parentId: '000000000000000a',
        id: '000000000000000b',
        kind: 'SERVER',
        localEndpoint: {serviceName: 'backend'},
        annotations: [],
        tags: {},
        shared: true
      }
    ]);
  });

  // some instrumentation send 64-bit length, while others 128-bit or padded.
  it('should merge mixed-length IDs', () => {
    const spans = mergeV2ById([
      {
        traceId: '11111111111111112222222222222222', // 128-bit
        id: '000000000000000a'
      },
      {
        traceId: '00000000000000002222222222222222', // padded
        parentId: '000000000000000a',
        id: '000000000000000b'
      },
      {
        traceId: '2222222222222222', // truncated
        parentId: '000000000000000b',
        id: '000000000000000c'
      }
    ]);

    expect(spans).to.deep.equal([
      {
        traceId: '11111111111111112222222222222222',
        id: '000000000000000a',
        annotations: [],
        tags: {}
      },
      {
        traceId: '11111111111111112222222222222222',
        parentId: '000000000000000a',
        id: '000000000000000b',
        annotations: [],
        tags: {}
      },
      {
        traceId: '11111111111111112222222222222222',
        parentId: '000000000000000b',
        id: '000000000000000c',
        annotations: [],
        tags: {}
      }
    ]);
  });

  /* Let's pretend people use crappy data, but only on the first hop. */
  it('should merge when missing endpoints', () => {
    const spans = mergeV2ById([
      {
        traceId: 'a',
        id: 'a',
        tags: {'span.kind': 'SERVER', service: 'frontend'}
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
        timestamp: 1,
        tags: {'span.kind': 'CLIENT', service: 'frontend'}
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
        kind: 'SERVER',
        localEndpoint: {serviceName: 'backend'},
        shared: true
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
        duration: 10
      }
    ]);

    expect(spans).to.deep.equal([
      {
        traceId: '000000000000000a',
        id: '000000000000000a',
        annotations: [],
        tags: {'span.kind': 'SERVER', service: 'frontend'}
      },
      {
        traceId: '000000000000000a',
        parentId: '000000000000000a',
        id: '000000000000000b',
        timestamp: 1,
        duration: 10,
        annotations: [],
        tags: {'span.kind': 'CLIENT', service: 'frontend'}
      },
      {
        traceId: '000000000000000a',
        parentId: '000000000000000a',
        id: '000000000000000b',
        kind: 'SERVER',
        localEndpoint: {serviceName: 'backend'},
        annotations: [],
        tags: {},
        shared: true
      }
    ]);
  });

  /*
   * If a client request is proxied by something that does transparent retried. It can be the case
   * that two servers share the same ID (accidentally!)
   */
  it('should not merge shared spans on different IPs', () => {
    const spans = mergeV2ById([
      {
        traceId: 'a',
        id: 'a',
        kind: 'SERVER',
        localEndpoint: {serviceName: 'frontend'}
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
        kind: 'CLIENT',
        timestamp: 1,
        localEndpoint: {serviceName: 'frontend'}
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
        kind: 'SERVER',
        localEndpoint: {serviceName: 'backend', ipv4: '1.2.3.4'},
        shared: true
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
        kind: 'SERVER',
        localEndpoint: {serviceName: 'backend', ipv4: '1.2.3.5'},
        shared: true
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
        duration: 10,
        localEndpoint: {serviceName: 'frontend'}
      }
    ]);

    expect(spans).to.deep.equal([
      {
        traceId: '000000000000000a',
        id: '000000000000000a',
        kind: 'SERVER',
        localEndpoint: {serviceName: 'frontend'},
        annotations: [],
        tags: {}
      },
      {
        traceId: '000000000000000a',
        parentId: '000000000000000a',
        id: '000000000000000b',
        kind: 'CLIENT',
        timestamp: 1,
        duration: 10,
        localEndpoint: {serviceName: 'frontend'},
        annotations: [],
        tags: {}
      },
      {
        traceId: '000000000000000a',
        parentId: '000000000000000a',
        id: '000000000000000b',
        kind: 'SERVER',
        localEndpoint: {serviceName: 'backend', ipv4: '1.2.3.4'},
        annotations: [],
        tags: {},
        shared: true
      },
      {
        traceId: '000000000000000a',
        parentId: '000000000000000a',
        id: '000000000000000b',
        kind: 'SERVER',
        localEndpoint: {serviceName: 'backend', ipv4: '1.2.3.5'},
        annotations: [],
        tags: {},
        shared: true
      }
    ]);
  });

  // Same as above, but the late reported data has no parent id or endpoint
  it('should put random data on first span with endpoint', () => {
    const spans = mergeV2ById([
      {
        traceId: 'a',
        id: 'a',
        kind: 'SERVER',
        localEndpoint: {serviceName: 'frontend'}
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
        kind: 'CLIENT',
        timestamp: 1,
        localEndpoint: {serviceName: 'frontend'}
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
        kind: 'SERVER',
        localEndpoint: {serviceName: 'backend', ipv4: '1.2.3.4'},
        shared: true
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
        kind: 'SERVER',
        localEndpoint: {serviceName: 'backend', ipv4: '1.2.3.5'},
        shared: true
      },
      {
        traceId: 'a',
        id: 'b',
        duration: 10
      }
    ]);

    expect(spans).to.deep.equal([
      {
        traceId: '000000000000000a',
        id: '000000000000000a',
        kind: 'SERVER',
        localEndpoint: {serviceName: 'frontend'},
        annotations: [],
        tags: {}
      },
      {
        traceId: '000000000000000a',
        parentId: '000000000000000a',
        id: '000000000000000b',
        kind: 'CLIENT',
        timestamp: 1,
        duration: 10,
        localEndpoint: {serviceName: 'frontend'},
        annotations: [],
        tags: {}
      },
      {
        traceId: '000000000000000a',
        parentId: '000000000000000a',
        id: '000000000000000b',
        kind: 'SERVER',
        localEndpoint: {serviceName: 'backend', ipv4: '1.2.3.4'},
        annotations: [],
        tags: {},
        shared: true
      },
      {
        traceId: '000000000000000a',
        parentId: '000000000000000a',
        id: '000000000000000b',
        kind: 'SERVER',
        localEndpoint: {serviceName: 'backend', ipv4: '1.2.3.5'},
        annotations: [],
        tags: {},
        shared: true
      }
    ]);
  });

  // not a good idea to send parts of a local endpoint separately, but this helps ensure data isn't
  // accidentally partitioned in a overly fine grain
  it('should merge incomplete endpoints', () => {
    const spans = mergeV2ById([
      {
        traceId: 'a',
        id: 'a',
        kind: 'SERVER',
        localEndpoint: {serviceName: 'frontend'}
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
        kind: 'CLIENT',
        localEndpoint: {serviceName: 'frontend'}
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
        localEndpoint: {ipv4: '1.2.3.4'}
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
        kind: 'SERVER',
        localEndpoint: {serviceName: 'backend'},
        shared: true
      },
      {
        traceId: 'a',
        parentId: 'a',
        id: 'b',
        localEndpoint: {ipv4: '1.2.3.5'},
        shared: true
      }
    ]);

    expect(spans).to.deep.equal([
      {
        traceId: '000000000000000a',
        id: '000000000000000a',
        kind: 'SERVER',
        localEndpoint: {serviceName: 'frontend'},
        annotations: [],
        tags: {}
      },
      {
        traceId: '000000000000000a',
        parentId: '000000000000000a',
        id: '000000000000000b',
        kind: 'CLIENT',
        localEndpoint: {serviceName: 'frontend', ipv4: '1.2.3.4'},
        annotations: [],
        tags: {}
      },
      {
        traceId: '000000000000000a',
        parentId: '000000000000000a',
        id: '000000000000000b',
        kind: 'SERVER',
        localEndpoint: {serviceName: 'backend', ipv4: '1.2.3.5'},
        annotations: [],
        tags: {},
        shared: true
      }
    ]);
  });

  // spans are reported depth first, so it is possible to see incomplete trees with no root.
  it('should work when missing root span', () => {
    const missingParentId = '000000000000000a';
    const spans = mergeV2ById([
      {
        traceId: 'a',
        parentId: missingParentId,
        id: 'b',
        kind: 'SERVER',
        localEndpoint: {serviceName: 'backend', ipv4: '1.2.3.4'}
      },
      {
        traceId: 'a',
        parentId: missingParentId,
        id: 'c',
        kind: 'SERVER',
        localEndpoint: {serviceName: 'backend'}
      }
    ]);

    expect(spans).to.deep.equal([
      {
        traceId: '000000000000000a',
        parentId: missingParentId,
        id: '000000000000000b',
        kind: 'SERVER',
        localEndpoint: {serviceName: 'backend', ipv4: '1.2.3.4'},
        annotations: [],
        tags: {}
      },
      {
        traceId: '000000000000000a',
        parentId: missingParentId,
        id: '000000000000000c',
        kind: 'SERVER',
        localEndpoint: {serviceName: 'backend'},
        annotations: [],
        tags: {}
      }
    ]);
  });

  it('should merge incomplete data', () => {
    // let's pretend the client flushed before completion
    const spans = mergeV2ById([
      serverSpan,
      {
        traceId: '0000000000000001',
        id: '0000000000000002',
        duration: 207000,
        remoteEndpoint: backend,
        annotations: [
          {timestamp: 1472470996403000, value: 'wr'}
        ]
      },
      {
        traceId: '0000000000000001',
        id: '0000000000000002',
        name: 'get',
        kind: 'CLIENT',
        timestamp: 1472470996199000,
        localEndpoint: frontend,
        annotations: [
          {timestamp: 1472470996238000, value: 'ws'}
        ],
        tags: {
          'http.path': '/api',
          'clnt/finagle.version': '6.45.0',
        }
      }
    ]);
    expect(spans).to.deep.equal([clientSpan, serverSpan]);
  });

  it('should order and de-dupe annotations', () => {
    const spans = mergeV2ById([
      {
        traceId: '1111111111111111',
        id: '000000000000000a',
        annotations: [
          {timestamp: 2, value: 'b'},
          {timestamp: 2, value: 'b'},
          {timestamp: 1, value: 'a'}
        ]
      },
      {
        traceId: '1111111111111111',
        id: '000000000000000a'
      },
      {
        traceId: '1111111111111111',
        id: '000000000000000a',
        annotations: [{timestamp: 1, value: 'a'}, {timestamp: 3, value: 'b'}]
      }
    ]);

    expect(spans).to.deep.equal([
      {
        traceId: '1111111111111111',
        id: '000000000000000a',
        annotations: [
          {timestamp: 1, value: 'a'},
          {timestamp: 2, value: 'b'},
          {timestamp: 3, value: 'b'}
        ],
        tags: {}
      }
    ]);
  });

  it('should order spans by shared, timestamp then name', () => {
    const spans = mergeV2ById([
      {
        traceId: '1111111111111111',
        parentId: '0000000000000001',
        id: '0000000000000002',
        name: 'c',
        timestamp: 3
      },
      {
        traceId: '1111111111111111',
        parentId: '0000000000000001',
        id: '3',
        name: 'b',
        timestamp: 2
      },
      {
        traceId: '1111111111111111',
        parentId: '0000000000000001',
        id: '0000000000000004',
        name: 'a',
        timestamp: 1,
        shared: true
      },
      {
        traceId: '1111111111111111',
        parentId: '0000000000000001',
        id: '0000000000000004',
        name: 'a',
        timestamp: 2
      }
    ]);

    expect(spans.map(s => `${s.id}-${s.shared === true}-${s.timestamp}`)).to.deep.equal([
      '0000000000000004-false-2', // unshared is first even if later!
      '0000000000000004-true-1',
      '0000000000000003-false-2',
      '0000000000000002-false-3'
    ]);
  });

  it('should order root first even if skewed timestamp', () => {
    const spans = mergeV2ById([
      {
        traceId: '1111111111111111',
        id: '0000000000000001',
        name: 'c',
        timestamp: 3
      },
      {
        traceId: '1111111111111111',
        id: '0000000000000002',
        parentId: '0000000000000001',
        name: 'b',
        timestamp: 2 // happens before its parent
      },
      {
        traceId: '1111111111111111',
        id: '0000000000000003',
        parentId: '0000000000000001',
        name: 'b',
        timestamp: 3
      }
    ]);

    expect(spans.map(s => s.id)).to.deep.equal([
      '0000000000000001',
      '0000000000000002',
      '0000000000000003'
    ]);
  });

  // in the case of shared spans, root could be a client
  it('should order earliest root first', () => {
    const spans = mergeV2ById([
      {
        traceId: '1111111111111111',
        id: '0000000000000001',
        name: 'server',
        timestamp: 1,
        shared: true
      },
      {
        traceId: '1111111111111111',
        id: '0000000000000001',
        name: 'client',
        timestamp: 1
      }
    ]);

    expect(spans.map(s => s.name)).to.deep.equal([
      'client',
      'server'
    ]);
  });
});
