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

import java.util.Iterator;
import java.util.NoSuchElementException;

import static zipkin.internal.Util.checkNotNull;

/**
 * adapted from guava's {@code com.google.common.collect.AbstractIterator}.
 */
public class PeekingIterator<T> implements Iterator<T> {

  private final Iterator<T> delegate;
  private PeekingIterator.State state = State.NOT_READY;
  private T next;

  /**
   * Constructor for use by subclasses.
   */
  public PeekingIterator(Iterator<T> delegate) {
    this.delegate = checkNotNull(delegate, "delegate");
  }

  protected T computeNext() {
    if (delegate.hasNext()) {
      return delegate.next();
    }
    return endOfData();
  }

  protected final T endOfData() {
    state = State.DONE;
    return null;
  }

  @Override
  public final boolean hasNext() {
    switch (state) {
      case DONE:
        return false;
      case READY:
        return true;
      default:
    }
    return tryToComputeNext();
  }

  private boolean tryToComputeNext() {
    next = computeNext();
    if (state != State.DONE) {
      state = State.READY;
      return true;
    }
    return false;
  }

  @Override
  public final T next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    state = State.NOT_READY;
    return next;
  }

  public T peek() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    return next;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }

  private enum State {
    /**
     * We have computed the next element and haven't returned it yet.
     */
    READY,

    /**
     * We haven't yet computed or have already returned the element.
     */
    NOT_READY,

    /**
     * We have reached the end of the data and are finished.
     */
    DONE,
  }
}
