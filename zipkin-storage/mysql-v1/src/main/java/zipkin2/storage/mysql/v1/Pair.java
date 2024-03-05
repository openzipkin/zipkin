/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.mysql.v1;

final class Pair {
  final long left, right;

  Pair(long left, long right) {
    this.left = left;
    this.right = right;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof Pair)) return false;
    Pair that = (Pair) o;
    return left == that.left && right == that.right;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= (int) (h$ ^ ((left >>> 32) ^ left));
    h$ *= 1000003;
    h$ ^= (int) (h$ ^ ((right >>> 32) ^ right));
    return h$;
  }
}
