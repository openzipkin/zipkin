const {SPAN_V1} = require('../js/spanConverter');

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

    const expected = {
      traceId: 'a',
      name: 'get',
      id: 'a',
      timestamp: 1,
      duration: 2,
      annotations: [
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
      ]
    };

    const spanV1 = SPAN_V1.convert(spanV2);
    expect(spanV1.annotations).to.deep.equal(expected.annotations);
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

    const expected = {
      traceId: 'a',
      name: 'get',
      id: 'a',
      timestamp: 1,
      duration: 2,
      annotations: [
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
          timestamp: 2,
          value: 'middle'
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
      ]
    };

    const spanV1 = SPAN_V1.convert(spanV2);
    expect(spanV1.annotations).to.deep.equal(expected.annotations);
  });

  it('should set SA annotation on client span', () => {
    const spanV2 = {
      traceId: 'a',
      name: 'get',
      id: 'a',
      kind: 'CLIENT',
      timestamp: 1,
      duration: 1,
      localEndpoint: {
        serviceName: 'portalservice',
        ipv4: '10.57.50.83',
        port: 8080
      },
      remoteEndpoint: {
        serviceName: 'there',
        ipv4: '10.57.50.84',
        port: 80
      }
    };

    const expected = {
      traceId: 'a',
      name: 'get',
      id: 'a',
      annotations: [
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
          timestamp: 2,
          value: 'cr'
        }
      ],
      binaryAnnotations: [
        {
          key: 'sa',
          value: true,
          endpoint: {
            serviceName: 'there',
            ipv4: '10.57.50.84',
            port: 80
          }
        }
      ]
    };

    const spanV1 = SPAN_V1.convert(spanV2);
    expect(spanV1.annotations).to.deep.equal(expected.annotations);
    expect(spanV1.binaryAnnotations).to.deep.equal(expected.binaryAnnotations);
  });

  it('should write timestamps for shared span', () => {
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

    const expected = {
      traceId: 'a',
      name: 'get',
      id: 'a',
      timestamp: 1,
      duration: 2
    };

    const spanV1 = SPAN_V1.convert(spanV2);
    expect(spanV1.timestamp).to.deep.equal(expected.timestamp);
    expect(spanV1.duration).to.deep.equal(expected.duration);
  });
});
