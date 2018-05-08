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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.Call;
import zipkin2.Span;
import zipkin2.storage.SpanConsumer;

import java.util.List;

public class KafkaSpanConsumer implements SpanConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaSpanStore.class);

    public KafkaSpanConsumer(KafkaStorage kafkaStorage) {
    }

    @Override
    public Call<Void> accept(List<Span> list) {
        LOG.info("Hey; I'm still implementing this! Be patient!");
        return null;
    }
}
