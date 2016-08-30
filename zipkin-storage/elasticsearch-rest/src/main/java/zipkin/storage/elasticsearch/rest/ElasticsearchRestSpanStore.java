/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.storage.elasticsearch.rest;

import com.google.common.util.concurrent.ListenableFuture;
import zipkin.DependencyLink;
import zipkin.Span;
import zipkin.internal.Nullable;
import zipkin.storage.QueryRequest;
import zipkin.storage.guava.GuavaSpanStore;

import java.util.List;

/**
 * Created by ddcbdevins on 8/25/16.
 */
public class ElasticsearchRestSpanStore implements GuavaSpanStore {
    @Override
    public ListenableFuture<List<List<Span>>> getTraces(QueryRequest request) {
        return null;
    }

    @Override
    public ListenableFuture<List<Span>> getTrace(long id) {
        return null;
    }

    @Override
    public ListenableFuture<List<Span>> getRawTrace(long traceId) {
        return null;
    }

    @Override
    public ListenableFuture<List<String>> getServiceNames() {
        return null;
    }

    @Override
    public ListenableFuture<List<String>> getSpanNames(String serviceName) {
        return null;
    }

    @Override
    public ListenableFuture<List<DependencyLink>> getDependencies(long endTs, @Nullable Long lookback) {
        return null;
    }
}
