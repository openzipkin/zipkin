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

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EncodingTest {

  @Test public void emptyList_json() {
    List<byte[]> encoded = Arrays.asList();
    assertThat(Encoding.JSON.listSizeInBytes(encoded))
      .isEqualTo(2 /* [] */);
  }

  @Test public void singletonList_json() {
    List<byte[]> encoded = Arrays.asList(new byte[10]);

    assertThat(Encoding.JSON.listSizeInBytes(encoded.get(0).length))
      .isEqualTo(2 /* [] */ + 10);
    assertThat(Encoding.JSON.listSizeInBytes(encoded))
      .isEqualTo(2 /* [] */ + 10);
  }

  @Test public void multiItemList_json() {
    List<byte[]> encoded = Arrays.asList(new byte[3], new byte[4], new byte[128]);
    assertThat(Encoding.JSON.listSizeInBytes(encoded))
      .isEqualTo(2 /* [] */ + 3 + 1 /* , */ + 4 + 1  /* , */ + 128);
  }

  @Test public void emptyList_proto3() {
    List<byte[]> encoded = Arrays.asList();
    assertThat(Encoding.PROTO3.listSizeInBytes(encoded))
      .isEqualTo(0);
  }

  @Test public void singletonList_proto3() {
    List<byte[]> encoded = Arrays.asList(new byte[10]);

    assertThat(Encoding.PROTO3.listSizeInBytes(encoded.get(0).length))
      .isEqualTo(1 + 1 /* tag, length */ + 10);
    assertThat(Encoding.PROTO3.listSizeInBytes(encoded))
      .isEqualTo(1 + 1 /* tag, length */ + 10);
  }

  @Test public void multiItemList_proto3() {
    List<byte[]> encoded = Arrays.asList(new byte[3], new byte[4], new byte[128]);
    assertThat(Encoding.PROTO3.listSizeInBytes(encoded))
      .isEqualTo(0
        + (1 + 1 /* tag, length */ + 3)
        + (1 + 1 /* tag, length */ + 4)
        + (1 + 2 /* tag, length */ + 128)
      );
  }

  @Test public void singletonList_proto3_big() {
    // Not good to have a 5MiB span, but lets test the length prefix
    int bigSpan = 5 * 1024 * 1024;
    assertThat(Encoding.PROTO3.listSizeInBytes(bigSpan))
      .isEqualTo(1 + 4 /* tag, length */ + bigSpan);

    // Terrible value in real life as this would be a 536 meg span!
    int twentyNineBitNumber = 536870911;
    assertThat(Encoding.PROTO3.listSizeInBytes(twentyNineBitNumber))
      .isEqualTo(1 + 5 /* tag, length */ + twentyNineBitNumber);
  }
}
