/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
package zipkin.storage.elasticsearch.http.integration;

import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import zipkin.Span;
import zipkin.TestObjects;
import zipkin.internal.CallbackCaptor;
import zipkin.storage.elasticsearch.http.ElasticsearchHttpStorage;
import zipkin.storage.elasticsearch.http.InternalForTests;

import static org.assertj.core.api.Assertions.assertThat;

abstract class ElasticsearchHttpNamesFallbackTest {

  /** Should maintain state between multiple calls within a test. */
  abstract ElasticsearchHttpStorage storage();

  /** Setup test data which has doesnt map the "servicespan" type */
  @Before
  public void clear() throws IOException {
    InternalForTests.clear(storage());
    CallbackCaptor<Void> callback = new CallbackCaptor<>();
    InternalForTests.oldConsumer(storage()).accept(TestObjects.TRACE, callback);
    callback.get();
  }

  @Test
  public void getServiceNames() throws Exception {
    accept(TestObjects.TRACE);

    assertThat(storage().spanStore().getServiceNames())
        .containsExactly("app", "db", "no_ip", "web");
  }

  @Test
  public void getSpanNames() throws Exception {
    accept(TestObjects.TRACE);

    assertThat(storage().spanStore().getSpanNames("app"))
        .containsExactly("get", "query");
  }

  void accept(List<Span> trace) throws Exception {
    CallbackCaptor<Void> callback = new CallbackCaptor<>();
    InternalForTests.oldConsumer(storage()).accept(trace, callback);
    callback.get();
  }
}
