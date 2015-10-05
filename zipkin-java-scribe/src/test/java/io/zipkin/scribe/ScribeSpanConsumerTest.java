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
package io.zipkin.scribe;

import io.zipkin.Annotation;
import io.zipkin.BinaryAnnotation;
import io.zipkin.BinaryAnnotation.Type;
import io.zipkin.Constants;
import io.zipkin.Endpoint;
import io.zipkin.Span;
import io.zipkin.internal.Nullable;
import io.zipkin.internal.Util;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static io.zipkin.internal.Util.checkNotNull;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class ScribeSpanConsumerTest {

  Endpoint zipkinQuery = Endpoint.create("zipkin-query", (127 << 24) | 1, 9411);
  Endpoint zipkinQuery0 = Endpoint.create("zipkin-query", (127 << 24) | 1, 0);

  /** Finagle's zipkin tracer breaks on a column width with a trailing newline */
  @Test
  public void decodesSpanGeneratedByFinagle() {
    List<Span> spans = new ArrayList<>();

    new ScribeSpanConsumer(input -> input.forEach(spans::add))
        .log(asList(new LogEntry("zipkin",
                "CgABq/sBMnzE048LAAMAAAAOZ2V0VHJhY2VzQnlJZHMKAATN0p+4EGfTdAoABav7ATJ8xNOPDwAGDAAAAAQKAAEABR/wq+2DeAsAAgAAAAJzcgwAAwgAAX8AAAEGAAIkwwsAAwAAAAx6aXBraW4tcXVlcnkAAAoAAQAFH/Cr7zj4CwACAAAIAGFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFh\n"
                    + "YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhDAADCAABfwAAAQYAAiTDCwADAAAADHppcGtpbi1xdWVyeQAACgABAAUf8KwLPyILAAIAAABOR2MoOSwwLlBTU2NhdmVuZ2UsMjAxNS0wOS0xNyAxMjozNzowMiArMDAwMCwzMDQubWlsbGlzZWNvbmRzKzc2Mi5taWNyb3NlY29uZHMpDAADCAABfwAAAQYAAiTDCwADAAAADHppcGtpbi1xdWVyeQAIAAQABKZ6AAoAAQAFH/CsDLfACwACAAAAAnNzDAADCAABfwAAAQYAAiTDCwADAAAADHppcGtpbi1xdWVyeQAADwAIDAAAAAULAAEAAAATc3J2L2ZpbmFnbGUudmVyc2lvbgsAAgAAAAY2LjI4LjAIAAMAAAAGDAAECAABfwAAAQYAAgAACwADAAAADHppcGtpbi1xdWVyeQAACwABAAAAD3Nydi9tdXgvZW5hYmxlZAsAAgAAAAEBCAADAAAAAAwABAgAAX8AAAEGAAIAAAsAAwAAAAx6aXBraW4tcXVlcnkAAAsAAQAAAAJzYQsAAgAAAAEBCAADAAAAAAwABAgAAX8AAAEGAAIkwwsAAwAAAAx6aXBraW4tcXVlcnkAAAsAAQAAAAJjYQsAAgAAAAEBCAADAAAAAAwABAgAAX8AAAEGAAL5YAsAAwAAAAx6aXBraW4tcXVlcnkAAAsAAQAAAAZudW1JZHMLAAIAAAAEAAAAAQgAAwAAAAMMAAQIAAF/AAABBgACJMMLAAMAAAAMemlwa2luLXF1ZXJ5AAACAAkAAA==\n"))
        );

    char[] as = new char[2048];
    Arrays.fill(as, 'a');
    String reallyLongAnnotation = new String(as);

    assertThat(spans).containsOnly(
        new Span.Builder()
            .traceId(-6054243957716233329L)
            .name("getTracesByIds")
            .id(-3615651937927048332L)
            .parentId(-6054243957716233329L)
            .addAnnotation(Annotation.create(1442493420635000L, Constants.SERVER_RECV, zipkinQuery))
            .addAnnotation(Annotation.create(1442493420747000L, reallyLongAnnotation, zipkinQuery))
            .addAnnotation(Annotation.create(1442493422583586L, "Gc(9,0.PSScavenge,2015-09-17 12:37:02 +0000,304.milliseconds+762.microseconds)", zipkinQuery))
            .addAnnotation(Annotation.create(1442493422680000L, Constants.SERVER_SEND, zipkinQuery))
            .addBinaryAnnotation(binaryAnnotation("srv/finagle.version", "6.28.0", zipkinQuery0))
            .addBinaryAnnotation(binaryAnnotation("srv/mux/enabled", true, zipkinQuery0))
            .addBinaryAnnotation(binaryAnnotation(Constants.SERVER_ADDR, true, zipkinQuery))
            .addBinaryAnnotation(binaryAnnotation(Constants.CLIENT_ADDR, true, Endpoint.create("zipkin-query", (127 << 24) | 1, 63840)))
            .addBinaryAnnotation(binaryAnnotation("numIds", 1, zipkinQuery))
            .debug(false)
            .build());
  }

  public static BinaryAnnotation binaryAnnotation(String key, String value, @Nullable Endpoint endpoint) {
    byte[] bytes = checkNotNull(value, "value").getBytes(Util.UTF_8);
    return BinaryAnnotation.create(key, bytes, Type.STRING, endpoint);
  }

  public static BinaryAnnotation binaryAnnotation(String key, boolean value, @Nullable Endpoint endpoint) {
    return BinaryAnnotation.create(key, value ? new byte[]{1} : new byte[]{0}, Type.BOOL, endpoint);
  }

  public static BinaryAnnotation binaryAnnotation(String key, int value, @Nullable Endpoint endpoint) {
    byte[] bytes = new byte[]{
        (byte) (value >>> 24),
        (byte) (value >>> 16),
        (byte) (value >>> 8),
        (byte) value};
    return BinaryAnnotation.create(key, bytes, Type.I32, endpoint);
  }
}
