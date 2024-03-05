/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.elasticsearch;

import com.linecorp.armeria.client.Endpoint;
import org.junit.jupiter.api.Test;

import static com.linecorp.armeria.common.SessionProtocol.HTTP;
import static com.linecorp.armeria.common.SessionProtocol.HTTPS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InitialEndpointSupplierTest {

  @Test void defaultIsLocalhost9200RegardlessOfSessionProtocol() {
    assertThat(new InitialEndpointSupplier(HTTP, null).get())
      .isEqualTo(Endpoint.of("localhost", 9200))
      .isEqualTo(new InitialEndpointSupplier(HTTPS, null).get());
  }

  @Test void usesNaturalHttpPortsWhenUrls() {
    assertThat(new InitialEndpointSupplier(HTTP, "http://localhost").get())
      .isEqualTo(Endpoint.of("localhost", 80));
    assertThat(new InitialEndpointSupplier(HTTPS, "https://localhost").get())
      .isEqualTo(Endpoint.of("localhost", 443));
  }

  @Test void defaultsPlainHostsToPort9200() {
    assertThat(new InitialEndpointSupplier(HTTP, "localhost").get())
      .isEqualTo(Endpoint.of("localhost", 9200));
    assertThat(new InitialEndpointSupplier(HTTPS, "localhost").get())
      .isEqualTo(Endpoint.of("localhost", 443));
  }

  /** This helps ensure old setups don't break (provided they have http port 9200 open) */
  @Test void coersesPort9300To9200() {
    assertThat(new InitialEndpointSupplier(HTTP, "localhost:9300").get())
      .isEqualTo(Endpoint.of("localhost", 9200));
  }

  @Test void parsesListOfLocalhosts() {
    String hostList = "localhost:9201,localhost:9202";
    assertThat(new InitialEndpointSupplier(HTTP, hostList).get().endpoints())
      .containsExactly(Endpoint.of("localhost", 9201), Endpoint.of("localhost", 9202))
      .containsExactlyElementsOf(new InitialEndpointSupplier(HTTPS, hostList).get().endpoints());
  }

  @Test void parsesListOfLocalhosts_skipsBlankEntry() {
    String hostList = "localhost:9201,,localhost:9202";
    assertThat(new InitialEndpointSupplier(HTTP, hostList).get().endpoints())
      .containsExactly(Endpoint.of("localhost", 9201), Endpoint.of("localhost", 9202))
      .containsExactlyElementsOf(new InitialEndpointSupplier(HTTPS, hostList).get().endpoints());
  }

  @Test void parsesEmptyListOfHosts_toDefault() {
    assertThat(new InitialEndpointSupplier(HTTP, "").get().endpoints())
      .containsExactly(Endpoint.of("localhost", 9200));
  }

  @Test void parsesListOfLocalhosts_failsWhenAllInvalid() {
    InitialEndpointSupplier supplier = new InitialEndpointSupplier(HTTP, ",");
    assertThatThrownBy(supplier::get)
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("No valid endpoints found in ES hosts: ,");
  }
}
