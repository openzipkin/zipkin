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
