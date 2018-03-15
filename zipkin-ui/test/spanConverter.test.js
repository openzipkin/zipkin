const {SPAN_V1} = require('../js/spanConverter');
const should = require('chai').should();

describe('SPAN v1 Conversion', () => {
  it('should transform correctly from v2 to v1', () => {
    const spanV2 = {
      traceId: 'a',
      name: 'get',
      id: 'c',
      parentId: 'b',
      kind: 'SERVER',
      timestamp: 1,
      duration: 1,
      localEndpoint: {
        serviceName: 'portalservice',
        ipv4: '10.57.50.83',
        port: 8080
      },
      tags: {
        warning: 'The cake is a lie'
      }
    };

    const expected = {
      traceId: 'a',
      name: 'get',
      id: 'c',
      parentId: 'b',
      annotations: [
        {
          endpoint: {
            serviceName: 'portalservice',
            ipv4: '10.57.50.83',
            port: 8080
          },
          timestamp: 1,
          value: 'sr'
        },
        {
          endpoint: {
            serviceName: 'portalservice',
            ipv4: '10.57.50.83',
            port: 8080,
          },
          timestamp: 2,
          value: 'ss'
        }
      ],
      binaryAnnotations: [
        {
          key: 'warning',
          value: 'The cake is a lie',
          endpoint: {
            serviceName: 'portalservice',
            ipv4: '10.57.50.83',
            port: 8080
          }
        }
      ]
    };

    const spanV1 = SPAN_V1.convert(spanV2);
    expect(spanV1.traceId).to.equal(expected.traceId);
    expect(spanV1.name).to.equal(expected.name);
    expect(spanV1.id).to.equal(expected.id);
    expect(spanV1.parentId).to.equal(expected.parentId);
    expect(spanV1.annotations).to.deep.equal(expected.annotations);
    expect(spanV1.binaryAnnotations).to.deep.equal(expected.binaryAnnotations);
  });

  it('should write CS/CR when no annotations exist', () => {
    const spanV2 = {
      traceId: 'a',
      name: 'get',
      id: 'a',
      kind: 'CLIENT',
      timestamp: 1,
      duration: 2,
      localEndpoint: {
        serviceName: 'portalservice',
        ipv4: '10.57.50.83',
        port: 8080
      }
    };

    const spanV1 = SPAN_V1.convert(spanV2);
    expect(spanV1.annotations).to.deep.equal([
      {
        endpoint: {
          serviceName: 'portalservice',
          ipv4: '10.57.50.83',
          port: 8080
        },
        timestamp: 1,
        value: 'cs'
      },
      {
        endpoint: {
          serviceName: 'portalservice',
          ipv4: '10.57.50.83',
          port: 8080,
        },
        timestamp: 3,
        value: 'cr'
      }
    ]);
  });

  it('should maintain CS/CR annotation order', () => {
    const spanV2 = {
      traceId: 'a',
      name: 'get',
      id: 'a',
      kind: 'CLIENT',
      timestamp: 1,
      duration: 2,
      localEndpoint: {
        serviceName: 'portalservice',
        ipv4: '10.57.50.83',
        port: 8080
      },
      annotations: [
        {
          timestamp: 2,
          value: 'middle'
        }
      ]
    };

    const spanV1 = SPAN_V1.convert(spanV2);
    expect(spanV1.annotations.map(s => s.timestamp)).to.deep.equal([1, 2, 3]);
  });

  it('should set SA annotation on client span', () => {
    const spanV2 = {
      traceId: 'a',
      id: 'a',
      kind: 'CLIENT',
      remoteEndpoint: {
        serviceName: 'there',
        ipv4: '10.57.50.84',
        port: 80
      }
    };

    const spanV1 = SPAN_V1.convert(spanV2);
    expect(spanV1.binaryAnnotations).to.deep.equal([
      {
        key: 'sa',
        value: true,
        endpoint: {
          serviceName: 'there',
          ipv4: '10.57.50.84',
          port: 80
        }
      }
    ]);
  });

  it('should not write timestamps for shared span', () => {
    const spanV2 = {
      traceId: 'a',
      name: 'get',
      id: 'a',
      kind: 'SERVER',
      shared: true,
      timestamp: 1,
      duration: 2,
      localEndpoint: {
        serviceName: 'portalservice',
        ipv4: '10.57.50.83',
        port: 8080
      }
    };

    const spanV1 = SPAN_V1.convert(spanV2);
    should.equal(spanV1.timestamp, undefined);
    should.equal(spanV1.duration, undefined);
  });
});
describe('SPAN v1 Merge', () => {
  const clientSpan = {
    traceId: 'a',
    name: 'get',
    id: 'c',
    parentId: 'b',
    annotations: [
      {
        endpoint: {
          serviceName: 'baloonservice',
          ipv4: '10.57.50.70',
          port: 80
        },
        timestamp: 1,
        value: 'cs'
      },
      {
        endpoint: {
          serviceName: 'baloonservice',
          ipv4: '10.57.50.70',
          port: 80,
        },
        timestamp: 4,
        value: 'cr'
      }
    ],
    binaryAnnotations: []
  };
  const serverSpan = {
    traceId: 'a',
    name: '',
    id: 'c',
    parentId: 'b',
    annotations: [
      {
        endpoint: {
          serviceName: 'portalservice',
          ipv4: '10.57.50.83',
          port: 8080
        },
        timestamp: 2,
        value: 'sr'
      },
      {
        endpoint: {
          serviceName: 'portalservice',
          ipv4: '10.57.50.83',
          port: 8080,
        },
        timestamp: 3,
        value: 'ss'
      }
    ],
    binaryAnnotations: []
  };
  const mergedSpan = {
    traceId: 'a',
    name: 'get',
    id: 'c',
    parentId: 'b',
    annotations: [
      {
        endpoint: {
          serviceName: 'baloonservice',
          ipv4: '10.57.50.70',
          port: 80
        },
        timestamp: 1,
        value: 'cs'
      },
      {
        endpoint: {
          serviceName: 'portalservice',
          ipv4: '10.57.50.83',
          port: 8080
        },
        timestamp: 2,
        value: 'sr'
      },
      {
        endpoint: {
          serviceName: 'portalservice',
          ipv4: '10.57.50.83',
          port: 8080,
        },
        timestamp: 3,
        value: 'ss'
      },
      {
        endpoint: {
          serviceName: 'baloonservice',
          ipv4: '10.57.50.70',
          port: 80,
        },
        timestamp: 4,
        value: 'cr'
      }
    ],
    binaryAnnotations: []
  };

  it('should merge server and client span', () => {
    const merged = SPAN_V1.merge(serverSpan, clientSpan);

    expect(merged).to.deep.equal(mergedSpan);
  });

  it('should merge client and server span', () => {
    const merged = SPAN_V1.merge(clientSpan, serverSpan);

    expect(merged).to.deep.equal(mergedSpan);
  });

  it('should overwrite client name with server name', () => {
    const merged = SPAN_V1.merge(clientSpan, {
      traceId: 'a',
      id: 'c',
      name: 'get /users/:userId',
      annotations: [{timestamp: 2, value: 'sr'}],
      binaryAnnotations: []
    });

    expect(merged.name).to.equal('get /users/:userId');
  });

  it('should not overwrite client name with empty', () => {
    const merged = SPAN_V1.merge(clientSpan, {
      traceId: 'a',
      id: 'c',
      name: '',
      annotations: [{timestamp: 2, value: 'sr'}],
      binaryAnnotations: []
    });

    expect(merged.name).to.equal(clientSpan.name);
  });

  it('should not overwrite client name with unknown', () => {
    const merged = SPAN_V1.merge(clientSpan, {
      traceId: 'a',
      id: 'c',
      name: 'unknown',
      annotations: [{timestamp: 2, value: 'sr'}],
      binaryAnnotations: []
    });

    expect(merged.name).to.equal(clientSpan.name);
  });
});
