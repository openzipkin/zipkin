const {
  ipsMatch,
  getClockSkew
} = require('../js/skew');
const {SpanNode} = require('../js/spanNode');
import {clean} from '../js/spanCleaner';
const should = require('chai').should();

// originally zipkin2.internal.CorrectForClockSkewTest.java
describe('treeCorrectedForClockSkew', () => {
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

  const ipv6 = {ipv6: '2001:db8::c001'};
  const ipv4 = {ipv4: '192.168.99.101'};
  const both = {
    ipv4: '192.168.99.101',
    ipv6: '2001:db8::c001'
  };

  it('IPs should not match when undefined', () => {
    expect(ipsMatch(undefined, undefined)).to.equal(false);
    expect(ipsMatch(undefined, ipv4)).to.equal(false);
    expect(ipsMatch(ipv4, undefined)).to.equal(false);
  });

  it('IPs should not match unless both sides have an IP', () => {
    const noIp = {serviceName: 'foo'};
    expect(ipsMatch(noIp, ipv4)).to.equal(false);
    expect(ipsMatch(noIp, ipv6)).to.equal(false);
    expect(ipsMatch(ipv4, noIp)).to.equal(false);
    expect(ipsMatch(ipv6, noIp)).to.equal(false);
  });

  it('IPs should not match when IPs are different', () => {
    const differentIpv6 = {ipv6: '2001:db8::c002'};
    const differentIpv4 = {ipv4: '192.168.99.102'};

    expect(ipsMatch(differentIpv4, ipv4)).to.equal(false);
    expect(ipsMatch(differentIpv6, ipv6)).to.equal(false);
    expect(ipsMatch(ipv4, differentIpv4)).to.equal(false);
    expect(ipsMatch(ipv6, differentIpv6)).to.equal(false);
  });

  it('IPs should match when ipv4 or ipv6 match', () => {
    expect(ipsMatch(ipv4, ipv4)).to.equal(true);
    expect(ipsMatch(both, ipv4)).to.equal(true);
    expect(ipsMatch(both, ipv6)).to.equal(true);
    expect(ipsMatch(ipv6, ipv6)).to.equal(true);
    expect(ipsMatch(ipv4, both)).to.equal(true);
    expect(ipsMatch(ipv6, both)).to.equal(true);
  });

  /*
   * Instrumentation bugs might result in spans that look like clock skew is at play. When skew
   * appears on the same host, we assume it is an instrumentation bug (rather than make it worse by
   * adjusting it!)
   */
  it('clock skew should only correct across different hosts', () => {
    const parent = new SpanNode(clean({
      traceId: '1',
      parentId: '2',
      id: '3',
      kind: 'CLIENT',
      localEndpoint: frontend,
      timestamp: 20,
      duration: 20
    }));
    const child = new SpanNode(clean({
      traceId: '1',
      parentId: '2',
      id: '3',
      kind: 'SERVER',
      localEndpoint: frontend,
      timestamp: 10, // skew
      duration: 10,
      shared: true
    }));
    parent.addChild(child);

    should.equal(getClockSkew(child), undefined);
  });

  it('clock skew should be attributed to the server endpoint', () => {
    const parent = new SpanNode(clean({
      traceId: '1',
      parentId: '2',
      id: '3',
      kind: 'CLIENT',
      localEndpoint: frontend,
      timestamp: 20,
      duration: 20
    }));
    const child = new SpanNode(clean({
      traceId: '1',
      parentId: '2',
      id: '3',
      kind: 'SERVER',
      localEndpoint: backend,
      timestamp: 10, // skew
      duration: 10,
      shared: true
    }));
    parent.addChild(child);

    // Skew correction pushes the server side forward, so the skew endpoint is the server
    expect(getClockSkew(child).endpoint).to.deep.equal(backend);
  });

  it('clock skew should be attributed to the server endpoint even if missing shared flag', () => {
    const parent = new SpanNode(clean({
      traceId: '1',
      parentId: '2',
      id: '3',
      kind: 'CLIENT',
      localEndpoint: frontend,
      timestamp: 20,
      duration: 20
    }));
    const child = new SpanNode(clean({
      traceId: '1',
      parentId: '2',
      id: '3',
      kind: 'SERVER',
      localEndpoint: backend,
      timestamp: 10, // skew
      duration: 10
    }));
    parent.addChild(child);

    // Skew correction pushes the server side forward, so the skew endpoint is the server
    expect(getClockSkew(child).endpoint).to.deep.equal(backend);
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
      duration: 20
    });
    const server = clean({
      traceId: '1',
      parentId: '2',
      id: '3',
      kind: 'SERVER',
      localEndpoint: backend,
      timestamp: 10, // skew
      duration: 10,
      shared: true
    });
    const parent = new SpanNode(client);
    const child = new SpanNode(server);
    parent.addChild(child);

    expect(getClockSkew(child).skew).to.equal(
      server.timestamp - client.timestamp // how much the server is behind
      - (client.duration - server.duration) / 2 // center the server by splitting what's left
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
      timestamp: 20
    });
    const server = clean({
      traceId: '1',
      parentId: '2',
      id: '3',
      kind: 'SERVER',
      localEndpoint: backend,
      timestamp: 10, // skew
      shared: true
    });
    const parent = new SpanNode(client);
    const child = new SpanNode(server);
    parent.addChild(child);

    expect(getClockSkew(child).skew).to.equal(
      server.timestamp - client.timestamp // how much server is behind
      - 1 // assume it takes at least 1us to get to the server
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
      duration: 5 // stops before server does
    });
    const server = clean({
      traceId: '1',
      id: '2',
      kind: 'SERVER',
      localEndpoint: backend,
      timestamp: 10, // skew
      duration: 10,
      shared: true
    });
    const parent = new SpanNode(client);
    const child = new SpanNode(server);
    parent.addChild(child);

    expect(getClockSkew(child).skew).to.equal(
      server.timestamp - client.timestamp // how much server is behind
      - 1 // assume it takes at least 1us to get to the server
    );
  });
});
