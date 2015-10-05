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
package io.zipkin.internal;

import io.zipkin.internal.Util.Serializer;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static io.zipkin.internal.Util.UTF_8;
import static io.zipkin.internal.Util.equal;
import static io.zipkin.internal.Util.writeJsonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UtilTest {
  @Test
  public void equalTest() {
    assertTrue(equal(null, null));
    assertTrue(equal("1", "1"));
    assertFalse(equal(null, "1"));
    assertFalse(equal("1", null));
    assertFalse(equal("1", "2"));
  }

  Serializer<List<Integer>> intsSerializer = writeJsonList(new Serializer<Integer>() {
    @Override
    public byte[] apply(Integer in) {
      return Integer.toString(in).getBytes(UTF_8);
    }
  });

  @Test
  public void writeJsonList_empty() {
    byte[] bytes = intsSerializer.apply(Arrays.<Integer>asList());
    assertThat(new String(bytes, UTF_8)).isEqualTo("[]");
  }

  @Test
  public void writeJsonList_one() {
    byte[] bytes = intsSerializer.apply(Arrays.asList(1));
    assertThat(new String(bytes, UTF_8)).isEqualTo("[1]");
  }

  @Test
  public void writeJsonList_two() {
    byte[] bytes = intsSerializer.apply(Arrays.asList(1, 2));
    assertThat(new String(bytes, UTF_8)).isEqualTo("[1,2]");
  }
}
