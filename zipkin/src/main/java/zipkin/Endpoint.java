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

import java.net.InetSocketAddress;
import zipkin.internal.JsonCodec;
import zipkin.internal.Nullable;
import zipkin.internal.Util;

import static zipkin.internal.Util.checkNotNull;

/**
 * Indicates the network context of a service recording an annotation with two exceptions.
 *
 * <p>When a BinaryAnnotation, and key is {@link Constants#CLIENT_ADDR} or {@link
 * Constants#SERVER_ADDR}, the endpoint indicates the source or destination of an RPC. This
 * exception allows zipkin to display network context of uninstrumented services, or clients such as
 * web browsers.
 */
public final class Endpoint {

  public static Endpoint create(String serviceName, int ipv4, int port) {
    return new Endpoint(serviceName, ipv4, (short) (port & 0xffff));
  }

  public static Endpoint create(String serviceName, int ipv4) {
    return new Endpoint(serviceName, ipv4, null);
  }

  /**
   * Classifier of a source or destination in lowercase, such as "zipkin-web".
   *
   * <p/>This is the primary parameter for trace lookup, so should be intuitive as possible, for
   * example, matching names in service discovery.
   *
   * <p/>Conventionally, when the service name isn't known, service_name = "unknown". However, it is
   * also permissible to set service_name = "" (empty string). The difference in the latter usage is
   * that the span will not be queryable by service name unless more information is added to the
   * span with non-empty service name, e.g. an additional annotation from the server.
   *
   * <p/>Particularly clients may not have a reliable service name at ingest. One approach is to set
   * service_name to "" at ingest, and later assign a better label based on binary annotations, such
   * as user agent.
   */
  public final String serviceName;

  /**
   * IPv4 endpoint address packed into 4 bytes.
   *
   * <p/>Ex for the IP 1.2.3.4, it would be {@code (1 << 24) | (2 << 16) | (3 << 8) | 4}
   *
   * @see java.net.Inet4Address#getAddress()
   */
  public final int ipv4;

  /**
   * IPv4 port or null, if not known.
   *
   * <p/>Note: this is to be treated as an unsigned integer, so watch for negatives.
   *
   * @see InetSocketAddress#getPort()
   */
  @Nullable
  public final Short port;

  Endpoint(String serviceName, int ipv4, Short port) {
    this.serviceName = checkNotNull(serviceName, "serviceName").toLowerCase();
    this.ipv4 = ipv4;
    this.port = port;
  }

  public static final class Builder {
    private String serviceName;
    private Integer ipv4;
    private Short port;

    public Builder() {
    }

    public Builder(Endpoint source) {
      this.serviceName = source.serviceName;
      this.ipv4 = source.ipv4;
      this.port = source.port;
    }

    /** @see Endpoint#serviceName */
    public Builder serviceName(String serviceName) {
      this.serviceName = serviceName;
      return this;
    }

    /** @see Endpoint#ipv4 */
    public Builder ipv4(int ipv4) {
      this.ipv4 = ipv4;
      return this;
    }

    /** @see Endpoint#port */
    public Builder port(Short port) {
      if (port == null || port != 0) {
        this.port = port;
      }
      return this;
    }

    public Endpoint build() {
      return new Endpoint(serviceName, ipv4, port);
    }
  }

  @Override
  public String toString() {
    return JsonCodec.ENDPOINT_ADAPTER.toJson(this);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Endpoint) {
      Endpoint that = (Endpoint) o;
      return (this.serviceName.equals(that.serviceName))
          && (this.ipv4 == that.ipv4)
          && Util.equal(this.port, that.port);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= serviceName.hashCode();
    h *= 1000003;
    h ^= ipv4;
    h *= 1000003;
    h ^= (port == null) ? 0 : port.hashCode();
    return h;
  }
}
