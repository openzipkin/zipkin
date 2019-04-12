/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
package zipkin2.storage.cassandra.internal;

import java.util.stream.Stream;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Reuses inputs from com.google.common.net.HostAndPortTest
public class HostAndPortTest {

  @Test public void parsesHost() {
    Stream.of(
      "google.com",
      "google.com",
      "192.0.2.1",
      "2001::3"
    ).forEach(host -> {
      assertThat(HostAndPort.fromString(host, 77))
        .isEqualTo(new HostAndPort(host, 77));
    });
  }

  @Test public void parsesHost_emptyPortOk() {
    assertThat(HostAndPort.fromString("gmail.com:", 77))
      .isEqualTo(new HostAndPort("gmail.com", 77));

    assertThat(HostAndPort.fromString("192.0.2.2:", 77))
      .isEqualTo(new HostAndPort("192.0.2.2", 77));

    assertThat(HostAndPort.fromString("[2001::2]:", 77))
      .isEqualTo(new HostAndPort("2001::2", 77));
  }

  @Test public void parsesHostAndPort() {
    assertThat(HostAndPort.fromString("gmail.com:77", 1))
      .isEqualTo(new HostAndPort("gmail.com", 77));

    assertThat(HostAndPort.fromString("192.0.2.2:77", 1))
      .isEqualTo(new HostAndPort("192.0.2.2", 77));

    assertThat(HostAndPort.fromString("[2001::2]:77", 1))
      .isEqualTo(new HostAndPort("2001::2", 77));
  }

  @Test public void throwsOnInvalidInput() {
    Stream.of(
      "google.com:65536",
      "google.com:9999999999",
      "google.com:port",
      "google.com:-25",
      "google.com:+25",
      "google.com:25  ",
      "google.com:25\t",
      "google.com:0x25 ",
      "[goo.gl]",
      "[goo.gl]:80",
      "[",
      "[]:",
      "[]:80",
      "[]bad",
      "[[:]]",
      "x:y:z",
      "",
      ":",
      ":123"
    ).forEach(hostPort -> {
      try {
        HostAndPort.fromString(hostPort, 77);
        throw new AssertionError(hostPort + " should have failed to parse");
      } catch (IllegalArgumentException e) {
      }
    });
  }
}
