const {Node, TreeBuilder} = require('../js/skew');
const should = require('chai').should();

// originally zipkin2.internal.NodeTest.java
describe('Node', () => {
  it('should construct without a value', () => {
    const value = {traceId: '1', id: '1'};
    const node = new Node(value);

    expect(node.value).to.equal(value);
  });

  it('should construct without a value', () => {
    const node = new Node();

    should.equal(node.value, undefined);
  });

  it('should not allow setting an undefined value', () => {
    const node = new Node();

    expect(() => node.setValue()).to.throw('newValue was undefined');
  });

  it('should not allow creating a cycle', () => {
    const fake = new Node();

    expect(() => fake.addChild(fake)).to.throw('circular dependency on Node()');

    const node = new Node({traceId: '1', id: '1'});

    expect(() => node.addChild(node))
      .to.throw('circular dependency on Node({"traceId":"1","id":"1"})');
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
    const a = new Node('a');
    const b = new Node('b');
    const c = new Node('c');
    const d = new Node('d');
    // root(a) has children b, c, d
    a.addChild(b);
    a.addChild(c);
    a.addChild(d);
    const e = new Node('e');
    const f = new Node('f');
    const g = new Node('g');
    // child(b) has children e, f, g
    b.addChild(e);
    b.addChild(f);
    b.addChild(g);
    const h = new Node('h');
    // f has no children
    // child(g) has child h
    g.addChild(h);

    expect(a.traverse()).to.deep.equal([
      'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'
    ]);
  });
});

describe('TreeBuilder', () => {
  // Makes sure that the trace tree is constructed based on parent-child, not by parameter order.
  it('should construct a trace tree', () => {
    const trace = [
      {traceId: 'a', id: 'a'},
      {traceId: 'a', parentId: 'a', id: 'b'},
      {traceId: 'a', parentId: 'b', id: 'c'},
    ];

    const treeBuilder = new TreeBuilder({traceId: 'a'});

    // TRACE is sorted with root span first, lets reverse them to make
    // sure the trace is stitched together by id.
    trace.slice(0).reverse().forEach((span) => treeBuilder.addNode(span.parentId, span.id, span));

    const root = treeBuilder.build();
    expect(root.value).to.equal(trace[0]);
    expect(root.children.map(n => n.value)).to.deep.equal([trace[1]]);

    const child = root.children[0];
    expect(child.children.map(n => n.value)).to.deep.equal([trace[2]]);
  });

  // input should be merged, but this ensures we are fine anyway
  it('should dedupe while constructing a trace tree', () => {
    const trace = [
      {traceId: 'a', id: 'a'},
      {traceId: 'a', id: 'a'},
      {traceId: 'a', id: 'a'}
    ];

    const treeBuilder = new TreeBuilder({traceId: 'a'});
    trace.forEach((span) => treeBuilder.addNode(span.parentId, span.id, span));
    const root = treeBuilder.build();

    expect(root.value).to.equal(trace[0]);
    expect(root.children.length).to.equal(0);
  });

  it('should allocate spans missing parents to root', () => {
    const trace = [
      {traceId: 'a', id: 'b'},
      {traceId: 'a', parentId: 'b', id: 'c'},
      {traceId: 'a', parentId: 'b', id: 'd'},
      {traceId: 'a', id: 'e'},
      {traceId: 'a', id: 'f'}
    ];

    const treeBuilder = new TreeBuilder({traceId: 'a'});
    trace.forEach((span) => treeBuilder.addNode(span.parentId, span.id, span));
    const root = treeBuilder.build();

    expect(root.traverse()).to.deep.equal(trace);
    expect(root.children.map(n => n.value)).to.deep.equal(trace.slice(1));
  });

  // spans are often reported depth-first, so it is possible to not have a root yet
  it('should construct a trace missing a root span', () => {
    const trace = [
      {traceId: 'a', parentId: 'a', id: 'b'},
      {traceId: 'a', parentId: 'a', id: 'c'},
      {traceId: 'a', parentId: 'a', id: 'd'}
    ];

    const treeBuilder = new TreeBuilder({traceId: 'a'});
    trace.forEach((span) => treeBuilder.addNode(span.parentId, span.id, span));
    const root = treeBuilder.build();

    should.equal(root.value, undefined);
    expect(root.traverse()).to.deep.equal(trace);
  });

  // input should be well formed, but this ensures we are fine anyway
  it('should skip on cycle', () => {
    const treeBuilder = new TreeBuilder({traceId: 'a'});
    expect(treeBuilder.addNode('b', 'b', {traceId: 'a', parentId: 'b', id: 'b'}))
      .to.equal(false);
  });
});
