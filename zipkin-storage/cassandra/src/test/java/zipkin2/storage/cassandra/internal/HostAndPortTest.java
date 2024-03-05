/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra.internal;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Reuses inputs from com.google.common.net.HostAndPortTest
class HostAndPortTest {

  @Test void parsesHost() {
    Stream.of(
      "google.com",
      "google.com",
      "192.0.2.1",
      "2001::3"
    ).forEach(host -> assertThat(HostAndPort.fromString(host, 77))
      .isEqualTo(new HostAndPort(host, 77)));
  }

  @Test void parsesHost_emptyPortOk() {
    assertThat(HostAndPort.fromString("gmail.com:", 77))
      .isEqualTo(new HostAndPort("gmail.com", 77));

    assertThat(HostAndPort.fromString("192.0.2.2:", 77))
      .isEqualTo(new HostAndPort("192.0.2.2", 77));

    assertThat(HostAndPort.fromString("[2001::2]:", 77))
      .isEqualTo(new HostAndPort("2001::2", 77));
  }

  @Test void parsesHostAndPort() {
    assertThat(HostAndPort.fromString("gmail.com:77", 1))
      .isEqualTo(new HostAndPort("gmail.com", 77));

    assertThat(HostAndPort.fromString("192.0.2.2:77", 1))
      .isEqualTo(new HostAndPort("192.0.2.2", 77));

    assertThat(HostAndPort.fromString("[2001::2]:77", 1))
      .isEqualTo(new HostAndPort("2001::2", 77));
  }

  @Test void throwsOnInvalidInput() {
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
