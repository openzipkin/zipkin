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
package zipkin.storage.cassandra3;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import zipkin.storage.cassandra3.Schema.TraceIdUDT;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Enclosed.class)
public class SchemaTest {

  public static class TraceIdUDTTest {

    @Test public void toString_whenNotHigh_16Chars() {
      assertThat(new TraceIdUDT(0L, 12345678L))
          .hasToString("0000000000bc614e");
    }

    @Test public void toString_whenHigh_32Chars() {
      assertThat(new TraceIdUDT(1234L, 5678L))
          .hasToString("00000000000004d2000000000000162e");
    }
  }
}
