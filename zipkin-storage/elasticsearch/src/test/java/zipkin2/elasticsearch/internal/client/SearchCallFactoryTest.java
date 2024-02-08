/*
 * Copyright 2015-2024 The OpenZipkin Authors
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
