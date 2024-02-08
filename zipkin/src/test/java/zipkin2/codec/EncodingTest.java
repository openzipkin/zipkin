/*
 * Copyright 2015-2024 The OpenZipkin Authors
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

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EncodingTest {

  @Test void emptyList_json() {
    List<byte[]> encoded = List.of();
    assertThat(Encoding.JSON.listSizeInBytes(encoded))
      .isEqualTo(2 /* [] */);
  }

  @Test void singletonList_json() {
    List<byte[]> encoded = List.of(new byte[10]);

    assertThat(Encoding.JSON.listSizeInBytes(encoded.get(0).length))
      .isEqualTo(2 /* [] */ + 10);
    assertThat(Encoding.JSON.listSizeInBytes(encoded))
      .isEqualTo(2 /* [] */ + 10);
  }

  @Test void multiItemList_json() {
    List<byte[]> encoded = List.of(new byte[3], new byte[4], new byte[128]);
    assertThat(Encoding.JSON.listSizeInBytes(encoded))
      .isEqualTo(2 /* [] */ + 3 + 1 /* , */ + 4 + 1  /* , */ + 128);
  }

  @Test void emptyList_proto3() {
    List<byte[]> encoded = List.of();
    assertThat(Encoding.PROTO3.listSizeInBytes(encoded))
      .isEqualTo(0);
  }

  // an entry in a list is a repeated field
  @Test void singletonList_proto3() {
    List<byte[]> encoded = List.of(new byte[10]);

    assertThat(Encoding.PROTO3.listSizeInBytes(encoded.get(0).length))
      .isEqualTo(10);
    assertThat(Encoding.PROTO3.listSizeInBytes(encoded))
      .isEqualTo(10);
  }

  // per ListOfSpans in zipkin2.proto
  @Test void multiItemList_proto3() {
    List<byte[]> encoded = List.of(new byte[3], new byte[4], new byte[128]);
    assertThat(Encoding.PROTO3.listSizeInBytes(encoded))
      .isEqualTo(3 + 4 + 128);
  }
}
