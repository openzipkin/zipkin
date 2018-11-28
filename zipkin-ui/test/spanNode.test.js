const {
  SpanNode,
  SpanNodeBuilder
} = require('../js/spanNode');
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

  /*
   * The following tree should traverse in alphabetical order
   *
   *          a
   *        / | \
   *       b  c  d
   *      /|\
   *     e f g
   *          \
   *           h
   */
  it('should traverse breadth first', () => {
    expect(a.traverse().map(s => s.id)).to.deep.equal([
      'a', 'b', 'c', 'd', 'e', 'f', '1', '2'
    ]);
  });

  /*
   * Use the same tree to check depths
   *
   *          a
   *        / | \
   *       b  c  d
   *      /|\
   *     e f g
   *          \
   *           h
   */
  it('should report correct max depth', () => {
    expect(a.maxDepth()).to.equal(4);
    expect(b.maxDepth()).to.equal(3);
    expect(c.maxDepth()).to.equal(1);
    expect(d.maxDepth()).to.equal(1);
    expect(e.maxDepth()).to.equal(1);
    expect(f.maxDepth()).to.equal(1);
    expect(g.maxDepth()).to.equal(2);
    expect(h.maxDepth()).to.equal(1);
  });

  it('should report correct max depth when headless', () => {
    const headless = new SpanNode();
    headless.addChild(a);
    expect(headless.maxDepth()).to.equal(a.maxDepth());
  });
});

// originally zipkin2.internal.SpanNodeTest.java
describe('SpanNodeBuilder', () => {
  it('should throw error on empty trace', () => {
    let error;
    try {
      new SpanNodeBuilder({}).build([]);
    } catch (err) {
      error = err;
    }

    expect(error.message).to.eql('Trace was empty');
  });

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
