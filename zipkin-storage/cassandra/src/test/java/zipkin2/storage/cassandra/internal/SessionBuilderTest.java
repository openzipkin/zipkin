/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra.internal;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.storage.cassandra.internal.SessionBuilder.parseContactPoints;

class SessionBuilderTest {
  @Test void contactPoints_defaultsToLocalhost() {
    assertThat(parseContactPoints("localhost"))
      .containsExactly(new InetSocketAddress("127.0.0.1", 9042));
  }

  @Test void contactPoints_defaultsToPort9042() {
    assertThat(parseContactPoints("1.1.1.1"))
      .containsExactly(new InetSocketAddress("1.1.1.1", 9042));
  }

  @Test void contactPoints_defaultsToPort9042_multi() {
    assertThat(parseContactPoints("1.1.1.1:9143,2.2.2.2"))
      .containsExactly(
        new InetSocketAddress("1.1.1.1", 9143), new InetSocketAddress("2.2.2.2", 9042));
  }

  @Test void contactPoints_hostAndPort() {
    assertThat(parseContactPoints("1.1.1.1:9142"))
      .containsExactly(new InetSocketAddress("1.1.1.1", 9142));
  }
}
