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

import static zipkin.internal.Util.checkNotNull;

/** Aids in converging streams which have 2-tuples, such as start/endTs and parent/spanId */
public final class Pair<T> {

  public static <T> Pair<T> create(T _1, T _2) {
    return new Pair<>(_1, _2);
  }

  public final T _1;
  public final T _2;

  private Pair(T _1, T _2) {
    this._1 = checkNotNull(_1, "_1");
    this._2 = checkNotNull(_2, "_2");
  }

  @Override
  public String toString() {
    return "(" + _1 + ", " + _2 + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Pair) {
      Pair that = (Pair) o;
      return this._1.equals(that._1) && this._2.equals(that._2);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= this._1.hashCode();
    h *= 1000003;
    h ^= this._2.hashCode();
    return h;
  }
}
