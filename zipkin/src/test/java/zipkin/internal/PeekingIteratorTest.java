/**
 * Copyright 2015-2017 The OpenZipkin Authors
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

import java.util.Iterator;
import java.util.NoSuchElementException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

public class PeekingIteratorTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void unmodifiable() {
    thrown.expect(UnsupportedOperationException.class);

    PeekingIterator<Boolean> it = TrueThenDone.INSTANCE.iterator();
    assertThat(it).containsExactly(true);
    it.remove();
  }

  @Test
  public void next() {
    thrown.expect(NoSuchElementException.class);

    PeekingIterator<Boolean> it = TrueThenDone.INSTANCE.iterator();
    assertThat(it).containsExactly(true);
    it.next();
  }

  @Test
  public void peek() {
    thrown.expect(NoSuchElementException.class);

    PeekingIterator<Boolean> it = TrueThenDone.INSTANCE.iterator();
    assertTrue(it.peek());
    assertThat(it).containsExactly(true);
    it.peek();
  }

  enum TrueThenDone implements Iterable<Boolean> {
    INSTANCE;

    @Override public PeekingIterator<Boolean> iterator() {
      return new PeekingIterator<>(new Iterator<Boolean>() {
        boolean val = true;

        @Override public boolean hasNext() {
          return val;
        }

        @Override public Boolean next() {
          if (val) {
            val = false;
            return true;
          }
          return false;
        }
      });
    }
  }
}
