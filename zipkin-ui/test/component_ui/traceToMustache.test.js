import {traceToMustache, getRootSpans} from '../../js/component_ui/traceToMustache';
const {SpanNode} = require('../../js/spanNode');
import {treeCorrectedForClockSkew} from '../../js/skew';
import {httpTrace} from './traceTestHelpers';

// renders data into a tree for traceMustache
const cleanedHttpTrace = treeCorrectedForClockSkew(httpTrace);

describe('traceToMustache', () => {
  it('should show logsUrl', () => {
    const {logsUrl} = traceToMustache(cleanedHttpTrace, 'http/url.com');
    logsUrl.should.equal('http/url.com');
  });

  it('should derive summary info', () => {
    const {traceId, durationStr, depth, spanCount, serviceNameAndSpanCounts} =
      traceToMustache(cleanedHttpTrace);

    traceId.should.equal('bb1f0e21882325b8');
    durationStr.should.equal('168.731ms');
    depth.should.equal(3);
    spanCount.should.equal(3);
    serviceNameAndSpanCounts.should.eql([
      {serviceName: 'backend', spanCount: 1},
      {serviceName: 'frontend', spanCount: 2}
    ]);
  });

  it('should derive summary info even when headless', () => {
    const headless = new SpanNode(); // headless as there's no root span

    // make a copy of the cleaned http trace as adding a child is a mutation
    treeCorrectedForClockSkew(httpTrace).children.forEach(child => headless.addChild(child));

    const {traceId, durationStr, depth, spanCount, serviceNameAndSpanCounts} =
      traceToMustache(headless);

    traceId.should.equal('bb1f0e21882325b8');
    durationStr.should.equal('111.121ms'); // client duration
    depth.should.equal(2);
    spanCount.should.equal(2);
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
    const testTrace = new SpanNode({
      traceId: '2480ccca8df0fca5',
      name: 'get',
      id: '2480ccca8df0fca5',
      timestamp: 1457186385375000,
      duration: 333000,
      localEndpoint: {serviceName: 'zipkin-query', ipv4: '127.0.0.1', port: 9411},
      tags: {lc: 'component'}
    });
    const {spans: [testSpan]} = traceToMustache(testTrace);
    testSpan.tags[0].key.should.equal('Local Component');
  });

  it('should not include empty Local Component annotations', () => {
    const testTrace = new SpanNode({
      traceId: '2480ccca8df0fca5',
      name: 'get',
      id: '2480ccca8df0fca5',
      timestamp: 1457186385375000,
      duration: 333000,
      localEndpoint: {serviceName: 'zipkin-query', ipv4: '127.0.0.1', port: 9411},
      tags: {}
    });
    const {spans: [testSpan]} = traceToMustache(testTrace);
    // skips empty Local Component, but still shows it as an address
    testSpan.tags[0].key.should.equal('Local Address');
  });

  it('should tolerate spans without tags', () => {
    const testTrace = new SpanNode({
      traceId: '2480ccca8df0fca5',
      name: 'get',
      id: '2480ccca8df0fca5',
      kind: 'SERVER',
      timestamp: 1457186385375000,
      duration: 333000,
      localEndpoint: {serviceName: 'zipkin-query', ipv4: '127.0.0.1', port: 9411},
      tags: {}
    });
    const {spans: [testSpan]} = traceToMustache(testTrace);
    testSpan.annotations[0].value.should.equal('Server Start');
    testSpan.annotations[1].value.should.equal('Server Finish');
  });
});

describe('get root spans', () => {
  it('should find root spans in a trace', () => {
    const testTrace = [{
      parentId: null, // root span (no parent)
      id: 1
    }, {
      parentId: 1,
      id: 2
    }, {
      parentId: 3, // root span (no parent with this id)
      id: 4
    }, {
      parentId: 4,
      id: 5
    }];

    const rootSpans = getRootSpans(testTrace);
    rootSpans.should.eql([{
      parentId: null,
      id: 1
    }, {
      parentId: 3,
      id: 4
    }]);
  });
});
