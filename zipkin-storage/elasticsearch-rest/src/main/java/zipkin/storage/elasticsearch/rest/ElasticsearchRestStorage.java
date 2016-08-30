package zipkin.storage.elasticsearch.rest;

import zipkin.Component;
import zipkin.storage.guava.LazyGuavaStorageComponent;

import java.io.IOException;

/**
 * Created by ddcbdevins on 8/25/16.
 */
public class ElasticsearchRestStorage extends LazyGuavaStorageComponent<ElasticsearchRestSpanStore, ElasticsearchRestSpanConsumer> {
    @Override
    protected ElasticsearchRestSpanStore computeGuavaSpanStore() {
        return null;
    }

    @Override
    protected ElasticsearchRestSpanConsumer computeGuavaSpanConsumer() {
        return null;
    }

    @Override
    public CheckResult check() {
        return null;
    }

    @Override
    public void close() throws IOException {

    }
}
