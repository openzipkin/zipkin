/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra;

import com.datastax.oss.driver.api.core.data.UdtValue;
import com.datastax.oss.driver.api.core.type.UserDefinedType;
import com.datastax.oss.driver.api.core.type.codec.MappingCodec;
import com.datastax.oss.driver.api.core.type.codec.TypeCodec;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;
import zipkin2.Endpoint;
import zipkin2.internal.Nullable;

import static zipkin2.storage.cassandra.CassandraUtil.inetAddressOrNull;

final class EndpointCodec extends MappingCodec<UdtValue, Endpoint> {

  EndpointCodec(TypeCodec<UdtValue> innerCodec) {
    super(innerCodec, GenericType.of(Endpoint.class));
  }

  @Override public UserDefinedType getCqlType() {
    return (UserDefinedType) super.getCqlType();
  }

  @Nullable @Override protected Endpoint innerToOuter(@Nullable UdtValue value) {
    if (value == null) return null;
    Endpoint.Builder builder =
      Endpoint.newBuilder().serviceName(value.getString("service")).port(value.getInt("port"));
    builder.parseIp(value.getInetAddress("ipv4"));
    builder.parseIp(value.getInetAddress("ipv6"));
    return builder.build();
  }

  @Nullable @Override protected UdtValue outerToInner(@Nullable Endpoint endpoint) {
    if (endpoint == null) return null;
    UdtValue result = getCqlType().newValue();
    result.setString("service", endpoint.serviceName());
    result.setInetAddress("ipv4", inetAddressOrNull(endpoint.ipv4(), endpoint.ipv4Bytes()));
    result.setInetAddress("ipv6", inetAddressOrNull(endpoint.ipv6(), endpoint.ipv6Bytes()));
    result.setInt("port", endpoint.portAsInt());
    return result;
  }
}
