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
package zipkin2.elasticsearch;

import static org.mockito.Mockito.mock;

/** opens package access for testing */
public final class Access {
  // saves on http response mocking, which is already tested in zipkin-storage-elasticsearch
  public static void pretendIndexTemplatesExist(ElasticsearchStorage storage) {
    storage.indexTemplates = mock(IndexTemplates.class); // assume index templates called before
  }
}
