const {SPAN_V1} = require('../js/spanConverter');
const should = require('chai').should();

describe('SPAN v1 Conversion', () => {
  it('converts root server span', () => {
    // let's pretend there was no caller, so we don't set shared flag
    const spanV2 = {
      traceId: 'a',
      name: 'get',
      id: 'b',
      kind: 'SERVER',
      timestamp: 1,
      duration: 1,
      localEndpoint: {
        serviceName: 'portalservice',
        ipv4: '10.57.50.83',
        port: 8080
      },
      tags: {
        'http.path': '/foo'
      }
    };

    const expected = {
      traceId: 'a',
      id: 'b',
      name: 'get',
      timestamp: 1,
      duration: 1,
      annotations: [
        {
          value: 'sr',
          timestamp: 1,
          endpoint: {
            serviceName: 'portalservice',
            ipv4: '10.57.50.83',
            port: 8080
          }
        },
        {
          value: 'ss',
          timestamp: 2,
          endpoint: {
            serviceName: 'portalservice',
            ipv4: '10.57.50.83',
            port: 8080
          }
        }
      ],
      binaryAnnotations: [
        {
          key: 'http.path',
          value: '/foo',
          endpoint: {
            serviceName: 'portalservice',
            ipv4: '10.57.50.83',
            port: 8080
          }
        }
      ]
    };

    const spanV1 = SPAN_V1.convert(spanV2);
    expect(spanV1).to.deep.equal(expected);
  });

  it('converts incomplete shared server span', () => {
    const spanV2 = {
      traceId: 'a',
      parentId: 'b',
      id: 'c',
      name: 'get',
      kind: 'SERVER',
      shared: true,
      timestamp: 1,
      localEndpoint: {
        serviceName: 'portalservice',
        ipv4: '10.57.50.83',
        port: 8080
      },
      tags: {
        'http.path': '/foo'
      }
    };

    const expected = {
      traceId: 'a',
      parentId: 'b',
      id: 'c',
      name: 'get',
      annotations: [
        {
          value: 'sr',
          timestamp: 1,
          endpoint: {
            serviceName: 'portalservice',
            ipv4: '10.57.50.83',
            port: 8080
          }
        }
      ],
      binaryAnnotations: [
        {
          key: 'http.path',
          value: '/foo',
          endpoint: {
            serviceName: 'portalservice',
            ipv4: '10.57.50.83',
            port: 8080
          }
        }
      ]
    };

    const spanV1 = SPAN_V1.convert(spanV2);
    expect(spanV1).to.deep.equal(expected);
  });

  it('converts client span', () => {
    const spanV2 = {
      traceId: 'a',
      name: 'get',
      id: 'b',
      kind: 'CLIENT',
      timestamp: 1,
      duration: 1,
      localEndpoint: {
        serviceName: 'portalservice',
        ipv4: '10.57.50.83',
        port: 8080
      },
      tags: {
        'http.path': '/foo'
      }
    };

    const expected = {
      traceId: 'a',
      id: 'b',
      name: 'get',
      timestamp: 1,
      duration: 1,
      annotations: [
        {
          value: 'cs',
          timestamp: 1,
          endpoint: {
            serviceName: 'portalservice',
            ipv4: '10.57.50.83',
            port: 8080
          }
        },
        {
          value: 'cr',
          timestamp: 2,
          endpoint: {
            serviceName: 'portalservice',
            ipv4: '10.57.50.83',
            port: 8080
          }
        }
      ],
      binaryAnnotations: [
        {
          key: 'http.path',
          value: '/foo',
          endpoint: {
            serviceName: 'portalservice',
            ipv4: '10.57.50.83',
            port: 8080
          }
        }
      ]
    };

    const spanV1 = SPAN_V1.convert(spanV2);
    expect(spanV1).to.deep.equal(expected);
  });

  it('converts incomplete client span', () => {
    const spanV2 = {
      traceId: 'a',
      parentId: 'b',
      id: 'c',
      name: 'get',
      kind: 'CLIENT',
      timestamp: 1,
      localEndpoint: {
        serviceName: 'portalservice',
        ipv4: '10.57.50.83',
        port: 8080
      },
      tags: {
        'http.path': '/foo'
      }
    };

    const expected = {
      traceId: 'a',
      parentId: 'b',
      id: 'c',
      name: 'get',
      timestamp: 1,
      annotations: [
        {
          value: 'cs',
          timestamp: 1,
          endpoint: {
            serviceName: 'portalservice',
            ipv4: '10.57.50.83',
            port: 8080
          }
        }
      ],
      binaryAnnotations: [
        {
          key: 'http.path',
          value: '/foo',
          endpoint: {
            serviceName: 'portalservice',
            ipv4: '10.57.50.83',
            port: 8080
          }
        }
      ]
    };

    const spanV1 = SPAN_V1.convert(spanV2);
    expect(spanV1).to.deep.equal(expected);
  });

  it('converts producer span', () => {
    const spanV2 = {
      traceId: 'a',
      name: 'publish',
      id: 'c',
      parentId: 'b',
      kind: 'PRODUCER',
      timestamp: 1,
      duration: 1,
      localEndpoint: {serviceName: 'frontend'},
      remoteEndpoint: {serviceName: 'rabbitmq'}
    };

    const expected = {
      traceId: 'a',
      parentId: 'b',
      id: 'c',
      name: 'publish',
      timestamp: 1,
      duration: 1,
      annotations: [
        {value: 'ms', timestamp: 1, endpoint: {serviceName: 'frontend'}},
        {value: 'ws', timestamp: 2, endpoint: {serviceName: 'frontend'}},
      ],
      binaryAnnotations: [
        {
          key: 'ma',
          value: true,
          endpoint: {serviceName: 'rabbitmq'}
        }
      ]
    };

    const spanV1 = SPAN_V1.convert(spanV2);
    expect(spanV1).to.deep.equal(expected);
  });

  it('converts incomplete producer span', () => {
    const spanV2 = {
      traceId: 'a',
      name: 'publish',
      id: 'c',
      parentId: 'b',
      kind: 'PRODUCER',
      timestamp: 1,
      localEndpoint: {serviceName: 'frontend'}
    };

    const expected = {
      traceId: 'a',
      parentId: 'b',
      id: 'c',
      name: 'publish',
      timestamp: 1,
      annotations: [{value: 'ms', timestamp: 1, endpoint: {serviceName: 'frontend'}}],
      binaryAnnotations: []
    };

    const spanV1 = SPAN_V1.convert(spanV2);
    expect(spanV1).to.deep.equal(expected);
  });

  it('converts consumer span', () => {
    const spanV2 = {
      traceId: 'a',
      name: 'next-message',
      id: 'c',
      parentId: 'b',
      kind: 'CONSUMER',
      timestamp: 1,
      duration: 1,
      localEndpoint: {serviceName: 'backend'},
      remoteEndpoint: {serviceName: 'rabbitmq'}
    };

    const expected = {
      traceId: 'a',
      parentId: 'b',
      id: 'c',
      name: 'next-message',
      timestamp: 1,
      duration: 1,
      annotations: [
        {value: 'wr', timestamp: 1, endpoint: {serviceName: 'backend'}},
        {value: 'mr', timestamp: 2, endpoint: {serviceName: 'backend'}},
      ],
      binaryAnnotations: [
        {
          key: 'ma',
          value: true,
          endpoint: {serviceName: 'rabbitmq'}
        }
      ]
    };

    const spanV1 = SPAN_V1.convert(spanV2);
    expect(spanV1).to.deep.equal(expected);
  });

  it('converts incomplete consumer span', () => {
    const spanV2 = {
      traceId: 'a',
      name: 'next-message',
      id: 'c',
      parentId: 'b',
      kind: 'CONSUMER',
      timestamp: 1,
      localEndpoint: {serviceName: 'backend'}
    };

    const expected = {
      traceId: 'a',
      parentId: 'b',
      id: 'c',
      name: 'next-message',
      timestamp: 1,
      annotations: [{value: 'mr', timestamp: 1, endpoint: {serviceName: 'backend'}}],
      binaryAnnotations: []
    };

    const spanV1 = SPAN_V1.convert(spanV2);
    expect(spanV1).to.deep.equal(expected);
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

  it('should retain ipv4 and ipv6 addresses', () => {
    const localEndpoint = {
      serviceName: 'there',
      ipv4: '10.57.50.84',
      ipv6: '2001:db8::c001',
      port: 80
    };

    const spanV2 = {
      traceId: 'a',
      id: 'a',
      kind: 'CLIENT',
      timestamp: 1,
      localEndpoint
    };

    const spanV1 = SPAN_V1.convert(spanV2);
    expect(spanV1.annotations.map(s => s.endpoint)).to.deep.equal([localEndpoint]);
  });

  it('should backfill empty endpoint serviceName', () => {
    const spanV2 = {
      traceId: 'a',
      id: 'a',
      kind: 'CLIENT',
      timestamp: 1,
      localEndpoint: {
        ipv6: '2001:db8::c001'
      }
    };

    const spanV1 = SPAN_V1.convert(spanV2);
    expect(spanV1.annotations.map(s => s.endpoint)).to.deep.equal([{
      serviceName: '',
      ipv6: '2001:db8::c001'
    }]);
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
