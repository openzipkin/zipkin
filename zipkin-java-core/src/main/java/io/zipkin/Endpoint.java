/**
 * Copyright 2015 The OpenZipkin Authors
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
package io.zipkin;

import com.facebook.swift.codec.ThriftConstructor;
import com.facebook.swift.codec.ThriftField;
import com.facebook.swift.codec.ThriftStruct;
import com.google.auto.value.AutoValue;
import java.net.InetSocketAddress;

/** Indicates the network context of a service recording an annotation. */
@AutoValue
@ThriftStruct(value = "Endpoint", builder = AutoValue_Endpoint.Builder.class)
public abstract class Endpoint {

  public static Builder builder() {
    return new AutoValue_Endpoint.Builder();
  }

  public static Builder builder(Endpoint source) {
    return new AutoValue_Endpoint.Builder(source);
  }

  /**
   * IPv4 host address packed into 4 bytes.
   *
   * <p/>Ex for the IP 1.2.3.4, it would be {@code (1 << 24) | (2 << 16) | (3 << 8) | 4}
   *
   * @see java.net.Inet4Address#getAddress()
   */
  @ThriftField(value = 1)
  public abstract int ipv4();

  /**
   * IPv4 port
   *
   * <p/>Note: this is to be treated as an unsigned integer, so watch for negatives.
   *
   * @see InetSocketAddress#getPort()
   */
  @ThriftField(value = 2)
  public abstract short port();

  /**
   * Service name, such as "memcache" or "zipkin-web"
   *
   * <p/>Note: Some implementations set this to "Unknown"
   */
  @ThriftField(value = 3)
  public abstract String serviceName();

  @AutoValue.Builder
  public interface Builder {

    @ThriftField
    Builder ipv4(int ipv4);

    @ThriftField
    Builder port(short port);

    @ThriftField
    Builder serviceName(String serviceName);

    @ThriftConstructor
    Endpoint build();
  }
}
