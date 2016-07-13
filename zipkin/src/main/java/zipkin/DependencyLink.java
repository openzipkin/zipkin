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
package zipkin;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import zipkin.internal.JsonCodec;

import static zipkin.internal.Util.checkNotNull;

public final class DependencyLink implements Serializable {
  private static final long serialVersionUID = 0L;

  public static DependencyLink create(String parent, String child, long callCount) {
    return new DependencyLink(parent, child, callCount);
  }

  /** parent service name (caller) */
  public final String parent;

  /** child service name (callee) */
  public final String child;

  /** calls made during the duration (in milliseconds) of this link */
  public final long callCount;

  DependencyLink(String parent, String child, long callCount) {
    this.parent = checkNotNull(parent, "parent").toLowerCase();
    this.child = checkNotNull(child, "child").toLowerCase();
    this.callCount = callCount;
  }

  public Builder toBuilder(){
    return new Builder(this);
  }

  public static Builder builder(){
    return new Builder();
  }

  public static final class Builder {
    private String parent;
    private String child;
    private long callCount;

    Builder() {
    }

    Builder(DependencyLink source) {
      this.parent = source.parent;
      this.child = source.child;
      this.callCount = source.callCount;
    }

    public Builder parent(String parent) {
      this.parent = parent;
      return this;
    }

    public Builder child(String child) {
      this.child = child;
      return this;
    }

    public Builder callCount(long callCount) {
      this.callCount = callCount;
      return this;
    }

    public DependencyLink build() {
      return new DependencyLink(parent, child, callCount);
    }
  }

  @Override
  public String toString() {
    return JsonCodec.DEPENDENCY_LINK_ADAPTER.toJson(this);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof DependencyLink) {
      DependencyLink that = (DependencyLink) o;
      return (this.parent.equals(that.parent))
          && (this.child.equals(that.child))
          && (this.callCount == that.callCount);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= parent.hashCode();
    h *= 1000003;
    h ^= child.hashCode();
    h *= 1000003;
    h ^= (callCount >>> 32) ^ callCount;
    return h;
  }

  // Since this is an immutable object, and we have thrift handy, defer to a serialization proxy.
  final Object writeReplace() throws ObjectStreamException {
    return new SerializedForm(Codec.THRIFT.writeDependencyLink(this));
  }

  static final class SerializedForm implements Serializable {
    private static final long serialVersionUID = 0L;

    private final byte[] bytes;

    SerializedForm(byte[] bytes) {
      this.bytes = bytes;
    }

    Object readResolve() throws ObjectStreamException {
      try {
        return Codec.THRIFT.readDependencyLink(bytes);
      } catch (IllegalArgumentException e) {
        throw new StreamCorruptedException(e.getMessage());
      }
    }
  }
}
