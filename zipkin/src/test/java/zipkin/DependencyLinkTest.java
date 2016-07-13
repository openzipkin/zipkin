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
package zipkin;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import okio.Buffer;
import okio.ByteString;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DependencyLinkTest {

  @Test
  public void serialization() throws Exception {
    DependencyLink link = TestObjects.LINKS.get(0);

    Buffer buffer = new Buffer();
    new ObjectOutputStream(buffer.outputStream()).writeObject(link);

    assertThat(new ObjectInputStream(buffer.inputStream()).readObject())
        .isEqualTo(link);
  }

  @Test
  public void serializationUsesThrift() throws Exception {
    DependencyLink link = TestObjects.LINKS.get(0);

    Buffer buffer = new Buffer();
    new ObjectOutputStream(buffer.outputStream()).writeObject(link);

    byte[] thrift = Codec.THRIFT.writeDependencyLink(link);

    assertThat(buffer.indexOf(ByteString.of(thrift)))
        .isPositive();
  }
}
