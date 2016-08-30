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
