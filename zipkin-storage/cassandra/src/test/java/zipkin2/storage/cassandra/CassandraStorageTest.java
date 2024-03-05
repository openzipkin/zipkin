/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra;

import com.datastax.oss.driver.api.core.AllNodesFailedException;
import com.datastax.oss.driver.api.core.auth.Authenticator;
import com.datastax.oss.driver.api.core.auth.ProgrammaticPlainTextAuthProvider;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import zipkin2.CheckResult;
import zipkin2.Component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CassandraStorageTest {

  @Test void authProvider_defaultsToNull() {
    assertThat(CassandraStorage.newBuilder().build().authProvider)
      .isNull();
  }

  @Test void usernamePassword_impliesNullDelimitedUtf8Bytes() throws Exception {
    ProgrammaticPlainTextAuthProvider authProvider =
      (ProgrammaticPlainTextAuthProvider) CassandraStorage.newBuilder()
        .username("bob")
        .password("secret")
        .build().authProvider;

    Authenticator authenticator =
      authProvider.newAuthenticator(mock(EndPoint.class), "serverAuthenticator");

    byte[] SASLhandshake = {0, 'b', 'o', 'b', 0, 's', 'e', 'c', 'r', 'e', 't'};
    assertThat(authenticator.initialResponse().toCompletableFuture().get())
      .extracting(ByteBuffer::array)
      .isEqualTo(SASLhandshake);
  }

  @Test void check_failsInsteadOfThrowing() {
    CheckResult result = CassandraStorage.newBuilder().contactPoints("1.1.1.1").build().check();

    assertThat(result.ok()).isFalse();
    assertThat(result.error()).isInstanceOf(AllNodesFailedException.class);
  }

  /**
   * The {@code toString()} of {@link Component} implementations appear in health check endpoints.
   * Since these are likely to be exposed in logs and other monitoring tools, care should be taken
   * to ensure {@code toString()} output is a reasonable length and does not contain sensitive
   * information.
   */
  @Test void toStringContainsOnlySummaryInformation() {
    try (CassandraStorage cassandra =
           CassandraStorage.newBuilder().contactPoints("1.1.1.1").build()) {

      assertThat(cassandra)
        .hasToString("CassandraStorage{contactPoints=1.1.1.1, keyspace=zipkin2}");
    }
  }
}
