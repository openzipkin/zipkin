/*
 * Copyright 2015-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin2.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Ignore;
import org.junit.Test;
import zipkin2.Endpoint;
import zipkin2.Span;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class SpanNodeTest {
  List<String> messages = new ArrayList<>();

  Logger logger = new Logger("", null) {
    {
      setLevel(Level.ALL);
    }

    @Override public void log(Level level, String msg) {
      assertThat(level).isEqualTo(Level.FINE);
      messages.add(msg);
    }
  };

  @Test(expected = NullPointerException.class)
  public void addChild_nullNotAllowed() {
    Span.Builder builder = Span.newBuilder().traceId("a");
    SpanNode a = new SpanNode(builder.id("a").build());
    a.addChild(null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void addChild_selfNotAllowed() {
    Span.Builder builder = Span.newBuilder().traceId("a");
    SpanNode a = new SpanNode(builder.id("a").build());
    a.addChild(a);
  }

  /** Ensures internal deduping occurs */
  @Test public void build_redundantIgnored() {
    Span.Builder builder = Span.newBuilder().traceId("a");
    List<Span> trace = asList(builder.id("a").build(), builder.id("b").build(), builder.build());

    SpanNode tree = new SpanNode.Builder(logger).build(trace);
    assertThat(tree.span).isEqualTo(trace.get(0));
    assertThat(tree.children()).extracting(SpanNode::span).containsExactly(trace.get(1));
  }

  /**
   * The following tree should traverse in alphabetical order
   *
   * <p><pre>{@code
   *          a
   *        / | \
   *       b  c  d
   *      /|\
   *     e f g
   *          \
   *           h
   * }</pre>
   */
  @Test public void traversesBreadthFirst() {
    Span.Builder builder = Span.newBuilder().traceId("a");
    SpanNode a = new SpanNode(builder.id("a").build());
    SpanNode b = new SpanNode(builder.id("b").build());
    SpanNode c = new SpanNode(builder.id("c").build());
    SpanNode d = new SpanNode(builder.id("d").build());
    // root(a) has children b, c, d
    a.addChild(b).addChild(c).addChild(d);
    SpanNode e = new SpanNode(builder.id("e").build());
    SpanNode f = new SpanNode(builder.id("f").build());
    SpanNode g = new SpanNode(builder.id("1").build());
    // child(b) has children e, f, g
    b.addChild(e).addChild(f).addChild(g);
    SpanNode h = new SpanNode(builder.id("2").build());
    // f has no children
    // child(g) has child h
    g.addChild(h);

    assertThat(a.traverse()).toIterable()
      .extracting(SpanNode::span)
      .extracting(s -> s.id().replaceAll("0", ""))
      .containsExactly("a", "b", "c", "d", "e", "f", "1", "2");
  }

  /**
   * Makes sure that the trace tree is constructed based on parent-child, not by parameter order.
   */
  @Test public void constructsTraceTree() {
    List<Span> trace = asList(
      Span.newBuilder().traceId("a").id("a").build(),
      Span.newBuilder().traceId("a").parentId("a").id("b").build(),
      Span.newBuilder().traceId("a").parentId("b").id("c").build(),
      Span.newBuilder().traceId("a").parentId("c").id("d").build()
    );
    assertAncestry(trace);
  }

  /** Same as {@link #constructsTraceTree()}, except with shared span ID */
  @Test public void constructsTraceTree_sharedId() {
    List<Span> trace = asList(
      Span.newBuilder().traceId("a").id("a").build(),
      Span.newBuilder().traceId("a").parentId("a").id("b").build(),
      Span.newBuilder().traceId("a").parentId("a").id("b").shared(true).build(),
      Span.newBuilder().traceId("a").parentId("b").id("c").build()
    );
    assertAncestry(trace);
  }

  @Test public void constructsTraceTree_sharedRootId() {
    List<Span> trace = asList(
      Span.newBuilder().traceId("a").id("a").build(),
      Span.newBuilder().traceId("a").id("a").shared(true).build(),
      Span.newBuilder().traceId("a").parentId("a").id("b").build(),
      Span.newBuilder().traceId("a").parentId("b").id("c").build()
    );
    assertAncestry(trace);
  }

  void assertAncestry(List<Span> trace) {
    SpanNode root = buildTree(trace);
    assertThat(root.span()).isEqualTo(trace.get(0));

    SpanNode current = root;
    for (int i = 1, length = trace.size() - 1; i < length; i++) {
      current = current.children.get(0);
      assertThat(current.span).isEqualTo(trace.get(i));
      assertThat(current.children).extracting(SpanNode::span)
        .containsExactly(trace.get(i + 1));
    }
  }

  @Test public void constructsTraceTree_qualifiesChildrenOfDuplicateServerSpans() {
    List<Span> trace = asList(
      Span.newBuilder().traceId("a").id("a").build(),
      Span.newBuilder().traceId("a").parentId("a").id("b").build(),
      localServiceName("foo", Span.newBuilder().traceId("a").parentId("a").id("b").shared(true)),
      localServiceName("bar", Span.newBuilder().traceId("a").parentId("a").id("b").shared(true)),
      localServiceName("bar", Span.newBuilder().traceId("a").parentId("b").id("c")),
      localServiceName("foo", Span.newBuilder().traceId("a").parentId("b").id("d"))
    );

    assertServerAncestry(trace);
  }

  @Test public void constructsTraceTree_qualifiesChildrenOfDuplicateServerSpans_mixedShared() {
    List<Span> trace = asList(
      Span.newBuilder().traceId("a").id("a").build(),
      Span.newBuilder().traceId("a").parentId("a").id("b").build(),
      localServiceName("foo", Span.newBuilder().traceId("a").parentId("b").id("c")),
      localServiceName("bar", Span.newBuilder().traceId("a").parentId("a").id("b").shared(true)),
      localServiceName("bar", Span.newBuilder().traceId("a").parentId("b").id("d")),
      localServiceName("foo", Span.newBuilder().traceId("a").parentId("c").id("e"))
    );

    assertServerAncestry(trace);
  }

  void assertServerAncestry(List<Span> trace) {
    SpanNode a = buildTree(trace);
    assertThat(a.span()).isEqualTo(trace.get(0));

    SpanNode b_client = a.children().get(0);
    assertThat(b_client.span()).isEqualTo(trace.get(1));
    assertThat(b_client.children()).extracting(SpanNode::span)
      .containsExactly(trace.get(3), trace.get(2));

    SpanNode b_server_bar = b_client.children().get(0);
    assertThat(b_server_bar.children()).extracting(SpanNode::span)
      .containsExactly(trace.get(4));

    SpanNode b_server_foo = b_client.children().get(1);
    assertThat(b_server_foo.children()).extracting(SpanNode::span)
      .containsExactly(trace.get(5));
  }

  static Span localServiceName(String serviceName, Span.Builder builder) {
    return builder.localEndpoint(Endpoint.newBuilder().serviceName(serviceName).build()).build();
  }

  SpanNode buildTree(List<Span> trace) {
    // TRACE is sorted with root span first, lets reverse them to make
    // sure the trace is stitched together by id.
    List<Span> copy = new ArrayList<>(trace);
    Collections.reverse(copy);

    return new SpanNode.Builder(logger).build(copy);
  }

  @Test public void constructsTraceTree_dedupes() {
    List<Span> trace = asList(
      Span.newBuilder().traceId("a").id("a").build(),
      Span.newBuilder().traceId("a").id("a").build(),
      Span.newBuilder().traceId("a").id("a").build()
    );

    SpanNode root = new SpanNode.Builder(logger).build(trace);

    assertThat(root.span())
      .isEqualTo(trace.get(0));
    assertThat(root.children())
      .isEmpty();
  }

  @Test public void constructsTraceTree_duplicateRoots() {
    List<Span> trace = asList(
      Span.newBuilder().traceId("a").id("a").build(),
      Span.newBuilder().traceId("a").id("b").build()
    );

    SpanNode root = new SpanNode.Builder(logger).build(trace);

    assertThat(root.span())
      .isEqualTo(trace.get(0));
    assertThat(root.children())
      .extracting(SpanNode::span)
      .containsExactly(trace.get(1));
  }

  @Test public void build_noChildLeftBehind() {
    List<Span> spans = asList(
      Span.newBuilder().traceId("a").id("b").name("root-0").build(),
      Span.newBuilder().traceId("a").parentId("b").id("c").name("child-0").build(),
      Span.newBuilder().traceId("a").parentId("b").id("d").name("child-1").build(),
      Span.newBuilder().traceId("a").id("e").name("lost-0").build(),
      Span.newBuilder().traceId("a").id("f").name("lost-1").build());
    int treeSize = 0;
    SpanNode tree = new SpanNode.Builder(logger).build(spans);
    Iterator<SpanNode> iter = tree.traverse();
    while (iter.hasNext()) {
      iter.next();
      treeSize++;
    }
    assertThat(treeSize).isEqualTo(spans.size());
    assertThat(messages).containsExactly(
      "building trace tree: traceId=000000000000000a",
      "attributing span missing parent to root: traceId=000000000000000a, rootSpanId=000000000000000b, spanId=000000000000000e",
      "attributing span missing parent to root: traceId=000000000000000a, rootSpanId=000000000000000b, spanId=000000000000000f"
    );
  }

  @Test public void build_headless() {
    Span s2 = Span.newBuilder().traceId("a").parentId("a").id("b").name("s2").build();
    Span s3 = Span.newBuilder().traceId("a").parentId("a").id("c").name("s3").build();
    Span s4 = Span.newBuilder().traceId("a").parentId("a").id("d").name("s4").build();

    SpanNode root = new SpanNode.Builder(logger).build(asList(s2, s3, s4));

    assertThat(root.span()).isNull();
    assertThat(root.children()).extracting(SpanNode::span)
      .containsExactly(s2, s3, s4);
    assertThat(messages).containsExactly(
      "building trace tree: traceId=000000000000000a",
      "substituting dummy node for missing root span: traceId=000000000000000a"
    );
  }

  @Test public void build_outOfOrder() {
    Span s2 = Span.newBuilder().traceId("a").parentId("a").id("b").name("s2").build();
    Span s3 = Span.newBuilder().traceId("a").parentId("a").id("c").name("s3").build();
    Span s4 = Span.newBuilder().traceId("a").parentId("a").id("d").name("s4").build();

    SpanNode root = new SpanNode.Builder(logger).build(asList(s2, s3, s4));

    assertThat(root.span()).isNull();
    assertThat(root.children()).extracting(SpanNode::span)
      .containsExactly(s2, s3, s4);
    assertThat(messages).containsExactly(
      "building trace tree: traceId=000000000000000a",
      "substituting dummy node for missing root span: traceId=000000000000000a"
    );
  }

  @Test @Ignore public void addNode_skipsOnCycle() {
    Span.newBuilder().traceId("a").parentId("d").id("b").name("s2").build();
    Span.newBuilder().traceId("a").parentId("b").id("d").name("s3").build();

    // TODO: see how spans like ^^ affect the node tree
  }

  // uses the same data as javascript
  @Test public void build_skewedTrace() {
    List<Span> httpTrace = asList(
      Span.newBuilder()
        .traceId("1e223ff1f80f1c69").parentId("74280ae0c10d8062").id("43210ae0c10d1234")
        .name("async")
        .timestamp(1470150004008762L)
        .duration(65000L)
        .localEndpoint(Endpoint.newBuilder().serviceName("serviceb").ip("192.0.0.0").build())
        .build(),
      Span.newBuilder()
        .traceId("1e223ff1f80f1c69").parentId("bf396325699c84bf").id("43210ae0c10d1234")
        .kind(Span.Kind.SERVER)
        .name("post")
        .timestamp(1541138169255688L)
        .duration(168731L)
        .localEndpoint(Endpoint.newBuilder().serviceName("serviceb").ip("192.0.0.0").build())
        .shared(true)
        .build(),
      Span.newBuilder()
        .traceId("1e223ff1f80f1c69").id("bb1f0e21882325b8")
        .kind(Span.Kind.SERVER)
        .name("get")
        .timestamp(1470150004071068L)
        .duration(99411L)
        .localEndpoint(Endpoint.newBuilder().serviceName("servicea").ip("127.0.0.0").build())
        .build(),
      Span.newBuilder()
        .traceId("1e223ff1f80f1c69").parentId("bb1f0e21882325b8").id("74280ae0c10d8062")
        .kind(Span.Kind.CLIENT)
        .name("post")
        .timestamp(1470150004074202L)
        .duration(94539L)
        .localEndpoint(Endpoint.newBuilder().serviceName("servicea").ip("127.0.0.0").build())
        .build());

    SpanNode root = new SpanNode.Builder(logger).build(httpTrace);
    assertThat(root.traverse()).toIterable().extracting(SpanNode::span)
      .containsExactlyInAnyOrderElementsOf(httpTrace);
  }

  @Test public void ordersChildrenByTimestamp() {
    List<Span> trace = asList(
      Span.newBuilder().traceId("a").id("1").build(),
      Span.newBuilder().traceId("a").parentId("1").id("a").name("a").timestamp(2L).build(),
      Span.newBuilder().traceId("a").parentId("1").id("b").name("b").timestamp(1L).build(),
      Span.newBuilder().traceId("a").parentId("1").id("c").name("c").build()
    );

    SpanNode root = new SpanNode.Builder(logger).build(trace);

    assertThat(root.children()).extracting(n -> n.span().name())
      .containsExactly("c", "b", "a"); // null first
  }

  @Test public void build_changingIps() {
    // This trace was taken from the middle of a real broken one, IDs and timestamps changed
    List<Span> httpTrace = asList(
      Span.newBuilder()
        .traceId("1").parentId("a").id("c")
        .kind(Span.Kind.SERVER)
        .timestamp(1)
        .localEndpoint(Endpoint.newBuilder().serviceName("my-service").ip("10.2.3.4").build())
        .shared(true)
        .build(),
      Span.newBuilder()
        .traceId("1").parentId("c").id("b")
        .kind(Span.Kind.CLIENT)
        .timestamp(2)
        // note the IP is different
        .localEndpoint(Endpoint.newBuilder().serviceName("my-service").ip("169.2.3.4").build())
        .build(),
      Span.newBuilder()
        .traceId("1").parentId("c").id("a")
        .timestamp(3)
        .localEndpoint(Endpoint.newBuilder().serviceName("my-service").ip("10.2.3.4").build())
        .build());

    SpanNode root = new SpanNode.Builder(logger).build(httpTrace);
    assertThat(root.traverse()).toIterable().extracting(SpanNode::span)
      .containsExactlyElementsOf(httpTrace);
  }
}
