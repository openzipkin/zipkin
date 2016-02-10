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
import static zipkin.internal.Util.equal;

/**
 * Internal type used by {@link DependencyLinker linker} that holds the minimum state needed to
 * aggregate {@link zipkin.DependencyLink dependency links}.
 */
// fields not exposed as public to further discourage use as a general type
public final class DependencyLinkSpan {

  /**
   * Indicates the primary span type.
   */
  enum Kind {
    CLIENT,
    /** The span includes a {@link zipkin.Constants#SERVER_RECV}. */
    SERVER,
    UNKNOWN
  }

  final Kind kind;
  @Nullable
  final Long parentId;
  final long spanId;
  @Nullable
  final String service;
  @Nullable
  final String peerService;

  DependencyLinkSpan(Kind kind, Long parentId, long spanId, String service, String peerService) {
    this.kind = checkNotNull(kind, "kind");
    this.parentId = parentId;
    this.spanId = spanId;
    this.service = service;
    this.peerService = peerService;
  }

  public static final class Builder {
    private final Long parentId;
    private final long spanId;
    private String srService;
    private String caService;
    private String saService;

    public Builder(Long parentId, long spanId) {
      this.spanId = spanId;
      this.parentId = parentId;
    }

    /**
     * {@link zipkin.Constants#SERVER_RECV} is the preferred name of server, and this is a
     * traditional span.
     */
    public Builder srService(String srService) {
      this.srService = srService;
      return this;
    }

    /**
     * {@link zipkin.Constants#CLIENT_ADDR} is read to see calls into the root span from
     * uninstrumented clients.
     */
    public Builder caService(String caService) {
      this.caService = caService;
      return this;
    }

    /**
     * {@link zipkin.Constants#SERVER_ADDR} is only read at the leaf, when a client calls an
     * un-instrumented server.
     */
    public Builder saService(String saService) {
      this.saService = saService;
      return this;
    }

    public DependencyLinkSpan build() {
      // Finagle labels two sides of the same socket ("ca", "sa") with the same name.
      // Skip the client side, so it isn't mistaken for a loopback request
      if (equal(saService, caService)) {
        caService = null;
      }
      if (srService != null) {
        return new DependencyLinkSpan(Kind.SERVER, parentId, spanId, srService, caService);
      } else if (saService != null) {
        return new DependencyLinkSpan(Kind.CLIENT, parentId, spanId, caService, saService);
      }
      return new DependencyLinkSpan(Kind.UNKNOWN, parentId, spanId, null, null);
    }
  }
}