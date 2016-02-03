/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import zipkin.Span;
import zipkin.TestObjects;

import static org.assertj.core.api.Assertions.assertThat;

public class NodeTest {

  /**
   * <p>The following tree should traverse in alphabetical order <pre>{@code
   *
   *          a
   *        / | \
   *       b  c  d
   *      /|\     \
   *     e f g     h
   * }</pre>
   */
  @Test
  public void traversesBreadthFirst() {
    Node<Character> a = new Node<Character>().value('a');
    Node<Character> b = new Node<Character>().value('b');
    Node<Character> c = new Node<Character>().value('c');
    Node<Character> d = new Node<Character>().value('d');
    // root(a) has children b, c, d
    a.addChild(b).addChild(c).addChild(d);
    Node<Character> e = new Node<Character>().value('e');
    Node<Character> f = new Node<Character>().value('f');
    Node<Character> g = new Node<Character>().value('g');
    // child(b) has children e, f, g
    b.addChild(e).addChild(f).addChild(g);
    Node<Character> h = new Node<Character>().value('h');
    // f has no children
    // child(g) has child h
    g.addChild(h);

    assertThat(a.traverse()).extracting(Node::value)
        .containsExactly('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h');
  }

  /**
   * Makes sure that the trace tree is constructed based on parent-child, not by parameter order.
   */
  @Test
  public void constructsTraceTree() {
    // TRACE is sorted with root span first, lets shuffle them to make
    // sure the trace is stitched together by id.
    List<Span> copy = new ArrayList<>(TestObjects.TRACE);

    Collections.shuffle(copy);

    Node<Span> root = Node.constructTree(copy);
    assertThat(root.value())
        .isEqualTo(TestObjects.TRACE.get(0));

    assertThat(root.children()).extracting(Node::value)
        .containsExactly(TestObjects.TRACE.get(1));

    Node<Span> child = root.children().iterator().next();
    assertThat(child.children()).extracting(Node::value)
        .containsExactly(TestObjects.TRACE.get(2));
  }
}
