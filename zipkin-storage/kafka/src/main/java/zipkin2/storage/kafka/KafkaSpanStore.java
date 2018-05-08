/**
 * Copyright 2015-2018 The OpenZipkin Authors
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

package zipkin2.storage.kafka;

import zipkin2.Call;
import zipkin2.DependencyLink;
import zipkin2.Span;
import zipkin2.storage.QueryRequest;
import zipkin2.storage.SpanStore;

import java.util.List;

/**
 * This class is intentionally not implemented at this time.
 */
public class KafkaSpanStore implements SpanStore {
    public KafkaSpanStore(KafkaStorage kafkaStorage) {
    }

    @Override
    public Call<List<List<Span>>> getTraces(QueryRequest queryRequest) {
        return null;
    }

    @Override
    public Call<List<Span>> getTrace(String s) {
        return null;
    }

    @Override
    public Call<List<String>> getServiceNames() {
        return null;
    }

    @Override
    public Call<List<String>> getSpanNames(String s) {
        return null;
    }

    @Override
    public Call<List<DependencyLink>> getDependencies(long l, long l1) {
        return null;
    }
}
