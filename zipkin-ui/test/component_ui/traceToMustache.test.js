import {traceToMustache} from '../../js/component_ui/traceToMustache';
const {SpanNode} = require('../../js/component_data/spanNode');
const {clean} = require('../../js/component_data/spanCleaner');
import {treeCorrectedForClockSkew} from '../../js/component_data/skew';
import {httpTrace, netflixTrace, frontend, backend} from '../component_ui/traceTestHelpers';

// renders data into a tree for traceMustache
const cleanedHttpTrace = treeCorrectedForClockSkew(httpTrace);
const cleanedNetflixTrace = treeCorrectedForClockSkew(netflixTrace);

describe('traceToMustache', () => {
  it('should show logsUrl', () => {
    const {logsUrl} = traceToMustache(cleanedHttpTrace, 'http/url.com');
    expect(logsUrl).to.equal('http/url.com');
  });

  it('should derive summary info', () => {
    const {traceId, durationStr, depth, serviceNameAndSpanCounts} =
      traceToMustache(cleanedHttpTrace);

    expect(traceId).to.equal('bb1f0e21882325b8');
    durationStr.should.equal('168.731ms');
    depth.should.equal(2); // number of span rows (distinct span IDs)
    serviceNameAndSpanCounts.should.eql([
      {serviceName: 'backend', spanCount: 1},
      {serviceName: 'frontend', spanCount: 2}
    ]);
  });

  it('should position incomplete spans at the correct offset', () => {
    const {spans} = traceToMustache(cleanedNetflixTrace);

    // the absolute values are not important, just checks that only the root span is at offset 0
    spans.map(s => s.left).should.eql(
      [0, 8.108108108108109, 16.216216216216218, 64.86486486486487]
    );
  });

  it('should derive summary info even when headless', () => {
    const headless = new SpanNode(); // headless as there's no root span

    // make a copy of the cleaned http trace as adding a child is a mutation
    treeCorrectedForClockSkew(httpTrace).children.forEach(child => headless.addChild(child));

    const {traceId, durationStr, depth, serviceNameAndSpanCounts} =
      traceToMustache(headless);

    expect(traceId).to.equal('bb1f0e21882325b8');
    durationStr.should.equal('111.121ms'); // client duration
    depth.should.equal(1); // number of span rows (distinct span IDs)
    serviceNameAndSpanCounts.should.eql([
      {serviceName: 'backend', spanCount: 1},
      {serviceName: 'frontend', spanCount: 1}
    ]);
  });

  it('should show human-readable annotation name', () => {
    const {spans: [testSpan]} = traceToMustache(cleanedHttpTrace);
    testSpan.annotations[0].value.should.equal('Server Start');
    testSpan.annotations[1].value.should.equal('Server Finish');
    testSpan.tags[4].key.should.equal('Client Address');
  });

  it('should tolerate spans without annotations', () => {
    const testTrace = new SpanNode(clean({
      traceId: '2480ccca8df0fca5',
      name: 'get',
      id: '2480ccca8df0fca5',
      timestamp: 1457186385375000,
      duration: 333000,
      localEndpoint: {serviceName: 'zipkin-query', ipv4: '127.0.0.1', port: 9411},
      tags: {lc: 'component'}
    }));
    const {spans: [testSpan]} = traceToMustache(testTrace);
    testSpan.tags[0].key.should.equal('Local Component');
  });

  it('should not include empty Local Component annotations', () => {
    const testTrace = new SpanNode(clean({
      traceId: '2480ccca8df0fca5',
      name: 'get',
      id: '2480ccca8df0fca5',
      timestamp: 1457186385375000,
      duration: 333000,
      localEndpoint: {serviceName: 'zipkin-query', ipv4: '127.0.0.1', port: 9411}
    }));
    const {spans: [testSpan]} = traceToMustache(testTrace);
    // skips empty Local Component, but still shows it as an address
    testSpan.tags[0].key.should.equal('Local Address');
  });

  it('should tolerate spans without tags', () => {
    const testTrace = new SpanNode(clean({
      traceId: '2480ccca8df0fca5',
      name: 'get',
      id: '2480ccca8df0fca5',
      kind: 'SERVER',
      timestamp: 1457186385375000,
      duration: 333000,
      localEndpoint: {serviceName: 'zipkin-query', ipv4: '127.0.0.1', port: 9411}
    }));
    const {spans: [testSpan]} = traceToMustache(testTrace);
    testSpan.annotations[0].value.should.equal('Server Start');
    testSpan.annotations[1].value.should.equal('Server Finish');
  });

  // TODO: we should really only allocate remote endpoints when on an uninstrumented link
  it('should count spans for any endpoint', () => {
    const testTrace = new SpanNode(clean({
      traceId: '2480ccca8df0fca5',
      id: '2480ccca8df0fca5',
      kind: 'CLIENT',
      timestamp: 1,
      localEndpoint: frontend,
      remoteEndpoint: frontend
    }));
    testTrace.addChild(new SpanNode(clean({
      traceId: '2480ccca8df0fca5',
      parentId: '2480ccca8df0fca5',
      id: 'bf396325699c84bf',
      name: 'foo',
      timestamp: 2,
      localEndpoint: backend
    })));

    const {serviceNameAndSpanCounts} = traceToMustache(testTrace);
    serviceNameAndSpanCounts.should.eql([
      {serviceName: 'backend', spanCount: 1},
      {serviceName: 'frontend', spanCount: 1}
    ]);
  });

  it('should count spans with no timestamp or duration', () => {
    const testTrace = new SpanNode(clean({
      traceId: '2480ccca8df0fca5',
      id: '2480ccca8df0fca5',
      kind: 'CLIENT',
      timestamp: 1, // root always needs a timestamp
      localEndpoint: frontend,
      remoteEndpoint: frontend
    }));
    testTrace.addChild(new SpanNode(clean({
      traceId: '2480ccca8df0fca5',
      parentId: '2480ccca8df0fca5',
      id: 'bf396325699c84bf',
      name: 'foo',
      localEndpoint: backend
    })));

    const {serviceNameAndSpanCounts} = traceToMustache(testTrace);
    serviceNameAndSpanCounts.should.eql([
      {serviceName: 'backend', spanCount: 1},
      {serviceName: 'frontend', spanCount: 1}
    ]);
  });

  /*
   * The following tree should not traverse in alphabetical order, as the UI intends to order by
   * timstamp, not by cause. The reason is that the UI is row based, and people are used to seeing
   * data descending to the right.
   *
   *          a
   *        / | \
   *       b  c  d
   *      /|\
   *     e f 1
   *          \
   *           2
   */
  it('should order spans by timestamp, root first, not by cause', () => {
    const a = new SpanNode(clean({traceId: '1', id: 'a', timestamp: 8}));
    const b = new SpanNode(clean({traceId: '1', id: 'b', parentId: 'a', timestamp: 7}));
    const c = new SpanNode(clean({traceId: '1', id: 'c', parentId: 'a', timestamp: 6}));
    const d = new SpanNode(clean({traceId: '1', id: 'd', parentId: 'a', timestamp: 5}));
    // root(a) has children b, c, d
    a.addChild(b);
    a.addChild(c);
    a.addChild(d);
    const e = new SpanNode(clean({traceId: '1', id: 'e', parentId: 'b', timestamp: 4}));
    const f = new SpanNode(clean({traceId: '1', id: 'f', parentId: 'b', timestamp: 3}));
    const g = new SpanNode(clean({traceId: '1', id: '1', parentId: 'b', timestamp: 2}));
    // child(b) has children e, f, g
    b.addChild(e);
    b.addChild(f);
    b.addChild(g);
    const h = new SpanNode(clean({traceId: '1', id: '2', parentId: '1', timestamp: 1}));
    // f has no children
    // child(g) has child h
    g.addChild(h);

    const {spans} = traceToMustache(a);
    expect(spans.map(s => s.timestamp)).to.eql([8, 1, 2, 3, 4, 5, 6, 7]);
  });
});

