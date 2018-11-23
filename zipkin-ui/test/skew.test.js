const {
  SpanNode,
  SpanNodeBuilder,
  ipsMatch,
  getClockSkew
} = require('../js/skew');
import {clean, mergeV2ById} from '../js/spanCleaner';
const should = require('chai').should();

// originally zipkin2.internal.SpanNodeTest.java
describe('SpanNode', () => {
  it('should construct without a span', () => {
    const span = {traceId: '1', id: '1'};
    const node = new SpanNode(span);

    expect(node.span).to.equal(span);
  });

  it('should construct without a span', () => {
    const node = new SpanNode();

    should.equal(node.span, undefined);
  });

  it('should not allow setting an undefined span', () => {
    const node = new SpanNode();

    expect(() => node.setSpan()).to.throw('span was undefined');
  });

  /*
   * The following tree should traverse in alphabetical order
   *
   *          a
   *        / | \
   *       b  c  d
   *      /|\     \
   *     e f g     h
   */
  it('should traverse breadth first', () => {
    const a = new SpanNode({traceId: '1', id: 'a'});
    const b = new SpanNode({traceId: '1', id: 'b'});
    const c = new SpanNode({traceId: '1', id: 'c'});
    const d = new SpanNode({traceId: '1', id: 'd'});
    // root(a) has children b, c, d
    a.addChild(b);
    a.addChild(c);
    a.addChild(d);
    const e = new SpanNode({traceId: '1', id: 'e'});
    const f = new SpanNode({traceId: '1', id: 'f'});
    const g = new SpanNode({traceId: '1', id: '1'});
    // child(b) has children e, f, g
    b.addChild(e);
    b.addChild(f);
    b.addChild(g);
    const h = new SpanNode({traceId: '1', id: '2'});
    // f has no children
    // child(g) has child h
    g.addChild(h);

    expect(a.traverse().map(s => s.id)).to.deep.equal([
      'a', 'b', 'c', 'd', 'e', 'f', '1', '2'
    ]);
  });
});

// originally zipkin2.internal.SpanNodeTest.java
describe('SpanNodeBuilder', () => {
  // Makes sure that the trace tree is constructed based on parent-child, not by parameter order.
  it('should construct a trace tree', () => {
    const trace = mergeV2ById([
      {traceId: 'a', id: 'a'},
      {traceId: 'a', parentId: 'a', id: 'b'},
      {traceId: 'a', parentId: 'b', id: 'c'},
      {traceId: 'a', parentId: 'c', id: 'd'}
    ]);

    // TRACE is sorted with root span first, lets reverse them to make
    // sure the trace is stitched together by id.
    const root = new SpanNodeBuilder({}).build(trace.slice(0).reverse());

    expect(root.span).to.deep.equal(trace[0]);
    expect(root.children.map(n => n.span)).to.deep.equal([trace[1]]);

    const child = root.children[0];
    expect(child.children.map(n => n.span)).to.deep.equal([trace[2]]);
  });

  // input should be merged, but this ensures we are fine anyway
  it('should dedupe while constructing a trace tree', () => {
    const trace = [
      {traceId: 'a', id: 'a'},
      {traceId: 'a', id: 'a'},
      {traceId: 'a', id: 'a'}
    ];

    const root = new SpanNodeBuilder({}).build(trace);

    expect(root.span).to.deep.equal(clean(trace[0]));
    expect(root.children.length).to.equal(0);
  });

  it('should allocate spans missing parents to root', () => {
    const trace = mergeV2ById([
      {traceId: 'a', id: 'b', timestamp: 1},
      {traceId: 'a', parentId: 'b', id: 'c', timestamp: 2},
      {traceId: 'a', parentId: 'b', id: 'd', timestamp: 3},
      {traceId: 'a', id: 'e', timestamp: 4},
      {traceId: 'a', id: 'f', timestamp: 5}
    ]);

    const root = new SpanNodeBuilder({}).build(trace);

    expect(root.traverse()).to.deep.equal(trace);
    expect(root.span.id).to.eql('000000000000000b');
    expect(root.children.map(n => n.span)).to.deep.equal(trace.slice(1));
  });

  // spans are often reported depth-first, so it is possible to not have a root yet
  it('should construct a trace missing a root span', () => {
    const trace = mergeV2ById([
      {traceId: 'a', parentId: 'a', id: 'b'},
      {traceId: 'a', parentId: 'a', id: 'c'},
      {traceId: 'a', parentId: 'a', id: 'd'}
    ]);

    const root = new SpanNodeBuilder({}).build(trace);

    should.equal(root.span, undefined);
    expect(root.traverse()).to.deep.equal(trace);
  });

  // input should be well formed, but this ensures we are fine anyway
  it('should skip on cycle', () => {
    const trace = [
      {traceId: 'a', parentId: 'b', id: 'b'},
    ];

    const root = new SpanNodeBuilder({}).build(trace);

    expect(root.span).to.deep.equal(
          {traceId: '000000000000000a', id: '000000000000000b', annotations: [], tags: {}});
    expect(root.children.length).to.equal(0);
  });
});

// originally zipkin2.internal.CorrectForClockSkewTest.java
describe('correctForClockSkew', () => {
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
