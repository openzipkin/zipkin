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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import zipkin.Span;
import zipkin.storage.guava.GuavaSpanConsumer;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class ElasticsearchRestSpanConsumer implements GuavaSpanConsumer {

    private RestClient client = RestClient.builder(new HttpHost("localhost", 9201, "http")).build();

    @Override
    public ListenableFuture<Void> accept(List<Span> spans) {
        if (spans.isEmpty()) {
            return Futures.immediateFuture(null);
        }

        for (Span s : spans) {
            HttpEntity entity = new NStringEntity(s.toString(), ContentType.APPLICATION_JSON);
            try {
                Response res = client.performRequest("PUT", String.format("/zipkin/span/%s?parent=%s", s.id, s.parentId), Collections.<String,String>emptyMap(), entity);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return null;
    }
}
