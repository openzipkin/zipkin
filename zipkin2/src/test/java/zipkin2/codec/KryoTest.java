/**
 * Copyright 2015-2018 The OpenZipkin Authors
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
package zipkin2.codec;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import org.junit.Test;
import zipkin2.Annotation;
import zipkin2.DependencyLink;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.TestObjects;

import static org.assertj.core.api.Assertions.assertThat;

public class KryoTest {

  @Test public void kryoJavaSerialization_annotation() {
    Kryo kryo = new Kryo();
    kryo.register(Annotation.class, new JavaSerializer());

    Output output = new Output(4096);
    kryo.writeObject(output, Annotation.create(1L, "foo"));
    output.flush();
    byte[] serialized = output.getBuffer();

    assertThat(kryo.readObject(new Input(serialized), Annotation.class))
      .isEqualTo(Annotation.create(1L, "foo"));
  }

  @Test public void kryoJavaSerialization_endpoint() {
    Kryo kryo = new Kryo();
    kryo.register(Endpoint.class, new JavaSerializer());

    Output output = new Output(4096);
    kryo.writeObject(output, TestObjects.BACKEND);
    output.flush();
    byte[] serialized = output.getBuffer();

    assertThat(kryo.readObject(new Input(serialized), Endpoint.class))
      .isEqualTo(TestObjects.BACKEND);
  }

  @Test public void kryoJavaSerialization_span() {
    Kryo kryo = new Kryo();
    kryo.register(Span.class, new JavaSerializer());

    Output output = new Output(4096);
    kryo.writeObject(output, TestObjects.CLIENT_SPAN);
    output.flush();
    byte[] serialized = output.getBuffer();

    assertThat(kryo.readObject(new Input(serialized), Span.class))
      .isEqualTo(TestObjects.CLIENT_SPAN);
  }

  @Test public void kryoJavaSerialization_dependencyLink() {
    Kryo kryo = new Kryo();
    kryo.register(DependencyLink.class, new JavaSerializer());

    DependencyLink link = DependencyLink.newBuilder().parent("client").child("server").callCount(2L)
      .errorCount(23L).build();

    Output output = new Output(4096);
    kryo.writeObject(output, link);
    output.flush();
    byte[] serialized = output.getBuffer();

    assertThat(kryo.readObject(new Input(serialized), DependencyLink.class))
      .isEqualTo(link);
  }

  /** Example test for how to use Kryo and reuse our encoders */
  public class JsonV2SpanSerializer extends Serializer<Span> {
    @Override public void write(Kryo kryo, Output output, Span span) {
      byte[] json = SpanBytesEncoder.JSON_V2.encode(span);
      output.writeInt(json.length);
      output.write(json);
    }

    @Override public Span read(Kryo kryo, Input input, Class<Span> type) {
      int length = input.readInt();
      byte[] json = input.readBytes(length);
      return SpanBytesDecoder.JSON_V2.decodeOne(json);
    }
  }

  @Test public void kryoJson2() {
    Kryo kryo = new Kryo();
    kryo.register(Span.class, new JsonV2SpanSerializer());

    Output output = new Output(4096);
    kryo.writeObject(output, TestObjects.CLIENT_SPAN);
    output.flush();
    byte[] serialized = output.getBuffer();

    assertThat(kryo.readObject(new Input(serialized), Span.class))
      .isEqualTo(TestObjects.CLIENT_SPAN);
  }
}
