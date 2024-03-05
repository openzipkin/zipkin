/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.elasticsearch.internal.client;

import com.linecorp.armeria.client.WebClient;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SearchCallFactoryTest {
  WebClient httpClient = mock(WebClient.class);

  SearchCallFactory client = new SearchCallFactory(new HttpCall.Factory(httpClient));

  /** Declaring queries alphabetically helps simplify amazon signature logic */
  @Test void lenientSearchOrdersQueryAlphabetically() {
    assertThat(client.lenientSearch(List.of("zipkin:span-2016-10-01"), null))
        .endsWith("/_search?allow_no_indices=true&expand_wildcards=open&ignore_unavailable=true");
  }
}
