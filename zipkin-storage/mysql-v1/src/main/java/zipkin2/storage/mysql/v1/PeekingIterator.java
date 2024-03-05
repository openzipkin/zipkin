/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.mysql.v1;

import java.util.Iterator;
import java.util.NoSuchElementException;

/** adapted from guava's {@code com.google.common.collect.AbstractIterator}. */
class PeekingIterator<T> implements Iterator<T> {

  private final Iterator<T> delegate;
  private PeekingIterator.State state = State.NOT_READY;
  private T next;

  PeekingIterator(Iterator<T> delegate) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    this.delegate = delegate;
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
    /** We have computed the next element and haven't returned it yet. */
    READY,

    /** We haven't yet computed or have already returned the element. */
    NOT_READY,

    /** We have reached the end of the data and are finished. */
    DONE,
  }
}
