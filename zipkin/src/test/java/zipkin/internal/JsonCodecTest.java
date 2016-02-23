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

import java.io.IOException;
import java.util.List;
import org.junit.Test;
import zipkin.CodecTest;
import zipkin.Span;
import zipkin.TestObjects;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public final class JsonCodecTest extends CodecTest {
  private final JsonCodec codec = new JsonCodec();

  @Override
  protected JsonCodec codec() {
    return codec;
  }

  @Test
  public void tracesRoundTrip() throws IOException {
    List<List<Span>> traces = asList(TestObjects.TRACE, TestObjects.TRACE);
    byte[] bytes = codec().writeTraces(traces);
    assertThat(codec().readTraces(bytes))
        .isEqualTo(traces);
  }

  @Test
  public void stringsRoundTrip() throws IOException {
    List<String> strings = asList("foo", "bar", "baz");
    byte[] bytes = codec().writeStrings(strings);
    assertThat(codec().readStrings(bytes))
        .isEqualTo(strings);
  }
}
