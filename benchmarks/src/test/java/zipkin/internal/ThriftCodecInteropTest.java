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

import com.twitter.zipkin.thriftjava.Annotation;
import com.twitter.zipkin.thriftjava.Endpoint;
import com.twitter.zipkin.thriftjava.Span;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import org.apache.thrift.TDeserializer;
import org.apache.thrift.TException;
import org.apache.thrift.TSerializer;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.junit.Test;
import zipkin.Codec;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.Constants.SERVER_RECV;
import static zipkin.Constants.SERVER_SEND;

public class ThriftCodecInteropTest {
  TSerializer serializer = new TSerializer(new TBinaryProtocol.Factory());
  TDeserializer deserializer = new TDeserializer(new TBinaryProtocol.Factory());

  @Test
  public void spanSerializationIsCompatible() throws UnknownHostException, TException {

    zipkin.Endpoint.Builder builder = zipkin.Endpoint.builder().serviceName("web").port(80);
    builder.parseIp("124.13.90.3");
    builder.parseIp("2001:db8::c001");
    zipkin.Endpoint zipkinEndpoint = builder.build();

    zipkin.Span zipkinSpan = zipkin.Span.builder().traceId(1L).traceIdHigh(2L).id(1L).name("get")
        .addAnnotation(zipkin.Annotation.create(1000, SERVER_RECV, zipkinEndpoint))
        .addAnnotation(zipkin.Annotation.create(1350, SERVER_SEND, zipkinEndpoint))
        .build();

    Endpoint thriftEndpoint = new Endpoint()
        .setService_name("web")
        .setIpv4(124 << 24 | 13 << 16 | 90 << 8 | 3)
        .setIpv6(Inet6Address.getByName("2001:db8::c001").getAddress())
        .setPort((short) 80);

    Span thriftSpan = new Span(1L, "get", 1L, asList(
        new Annotation(1000, SERVER_RECV).setHost(thriftEndpoint),
        new Annotation(1350, SERVER_SEND).setHost(thriftEndpoint)), asList()).setTrace_id_high(2L);

    assertThat(serializer.serialize(thriftSpan))
        .isEqualTo(Codec.THRIFT.writeSpan(zipkinSpan));

    assertThat(Codec.THRIFT.writeSpan(zipkinSpan))
        .isEqualTo(serializer.serialize(thriftSpan));

    Span deserializedThrift = new Span();
    deserializer.deserialize(deserializedThrift, Codec.THRIFT.writeSpan(zipkinSpan));
    assertThat(deserializedThrift)
        .isEqualTo(thriftSpan);

    assertThat(Codec.THRIFT.readSpan(serializer.serialize(thriftSpan)))
        .isEqualTo(zipkinSpan);
  }
}
