const {mergeV2ById} = require('../js/spanCleaner');

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
        annotations: [{timestamp: 2, value: 'b'}, {timestamp: 1, value: 'a'}]
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

  it('should order spans by timestamp then name', () => {
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
        timestamp: 2
      }
    ]);

    expect(spans.map(s => s.id)).to.deep.equal([
      '0000000000000004',
      '0000000000000003',
      '0000000000000002',
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
